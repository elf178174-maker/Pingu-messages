package app.pingu.messages.platform.messaging

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import app.pingu.messages.core.text.QuotedReply
import app.pingu.messages.data.mms.pdu.PduCharsets
import app.pingu.messages.data.mms.pdu.PduHeaders
import app.pingu.messages.data.repository.SyncRepository
import app.pingu.messages.data.telephony.MmsProviderDataSource
import app.pingu.messages.data.telephony.SimDataSource
import app.pingu.messages.data.telephony.SmsProviderDataSource
import app.pingu.messages.data.telephony.ThreadsDataSource
import app.pingu.messages.domain.model.AppError
import app.pingu.messages.domain.model.AppSettings
import app.pingu.messages.domain.model.GroupMessagingMode
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.domain.model.Outcome
import app.pingu.messages.platform.mms.MmsAttachmentEncoder
import app.pingu.messages.platform.mms.MmsStatusReceiver
import app.pingu.messages.platform.mms.MmsTransport
import app.pingu.messages.platform.sms.SmsTransport
import app.pingu.messages.platform.system.DefaultSmsAppManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Decides how a message should travel and drives the send.
 *
 * The rules are the ones a person would expect and the ones the platform imposes:
 *
 *  * anything with an attachment or a subject is an MMS, because SMS cannot carry either;
 *  * several recipients become one MMS when "group messaging" is on, so replies reach everyone,
 *    and separate SMS messages otherwise;
 *  * a long body is split into concatenated SMS parts unless the user chose to send long messages
 *    as MMS instead.
 *
 * Every outgoing message is written to the system provider *before* it is handed to the radio, so a
 * message is never lost if the process dies mid-send, and so it is visible to any other messaging
 * app immediately.
 */
class MessageSender(
    private val context: Context,
    private val settingsProvider: suspend () -> AppSettings,
    private val sims: SimDataSource,
    private val threads: ThreadsDataSource,
    private val smsProvider: SmsProviderDataSource,
    private val mmsProvider: MmsProviderDataSource,
    private val smsTransport: SmsTransport,
    private val mmsTransport: MmsTransport,
    private val attachmentEncoder: MmsAttachmentEncoder,
    private val syncRepository: SyncRepository,
    private val defaultSmsApp: DefaultSmsAppManager,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun send(request: SendRequest): Outcome<SendResult> = withContext(ioDispatcher) {
        if (!defaultSmsApp.isDefault()) return@withContext Outcome.Failure(AppError.NotDefaultSmsApp)

        val recipients = request.recipients.map { it.trim() }.filter { it.isNotEmpty() }
        if (recipients.isEmpty()) return@withContext Outcome.Failure(AppError.Unexpected())

        if (sims.hasTelephony() && !sims.hasReadySim()) {
            return@withContext Outcome.Failure(AppError.NoSim)
        }

        val settings = settingsProvider()
        val subscriptionId = resolveSubscription(request.subscriptionId, settings)
        val body = composeBody(request, settings)

        val threadId = request.threadId?.takeIf { it > 0 }
            ?: threads.getOrCreateThreadId(recipients)
            ?: return@withContext Outcome.Failure(AppError.Unexpected())

        val needsMms = requiresMms(request, recipients, body, settings, subscriptionId)
        return@withContext if (needsMms) {
            sendAsMms(threadId, recipients, body, request, settings, subscriptionId)
        } else {
            sendAsSms(threadId, recipients, body, request, settings, subscriptionId)
        }
    }

    /** Re-sends a message that previously failed, reusing its content. */
    suspend fun retry(request: SendRequest): Outcome<SendResult> = send(request)

    private fun requiresMms(
        request: SendRequest,
        recipients: List<String>,
        body: String,
        settings: AppSettings,
        subscriptionId: Int,
    ): Boolean {
        if (request.attachments.isNotEmpty()) return true
        if (!request.subject.isNullOrBlank()) return true
        if (recipients.size > 1 &&
            settings.groupMessagingMode == GroupMessagingMode.GROUP_MMS &&
            mmsTransport.isGroupMmsEnabled(subscriptionId)
        ) {
            return true
        }
        if (!settings.splitLongMessages &&
            smsTransport.partCount(body, subscriptionId) > MAX_PARTS_BEFORE_MMS
        ) {
            return true
        }
        return false
    }

    private suspend fun sendAsSms(
        threadId: Long,
        recipients: List<String>,
        body: String,
        request: SendRequest,
        settings: AppSettings,
        subscriptionId: Int,
    ): Outcome<SendResult> {
        val localIds = ArrayList<Long>(recipients.size)
        val now = System.currentTimeMillis()

        for (recipient in recipients) {
            // Individual SMS to several people belong to their own one-to-one threads, which is
            // what makes replies come back privately.
            val targetThreadId = if (recipients.size == 1) {
                threadId
            } else {
                threads.getOrCreateThreadId(listOf(recipient)) ?: threadId
            }

            val messageUri = smsProvider.insertOutgoing(
                address = recipient,
                body = body,
                timestampMillis = now,
                subscriptionId = subscriptionId,
                threadId = targetThreadId,
            )
            val localId = messageUri?.let { syncRepository.syncSingleSms(it) } ?: 0L
            if (localId > 0) {
                localIds.add(localId)
                applyReplyLink(localId, request)
            }

            val result = smsTransport.send(
                destination = recipient,
                body = body,
                subscriptionId = subscriptionId,
                messageUri = messageUri,
                localMessageId = localId,
                threadId = targetThreadId,
                requestDeliveryReport = settings.deliveryReports,
            )
            if (result.isFailure) {
                messageUri?.let { smsProvider.markFailed(it, ERROR_CODE_LOCAL_FAILURE) }
                messageUri?.let { syncRepository.syncSingleSms(it) }
                return Outcome.Failure(AppError.SendFailed(ERROR_CODE_LOCAL_FAILURE))
            }
        }
        return Outcome.Success(SendResult(threadId, localIds, usedMms = false))
    }

    private suspend fun sendAsMms(
        threadId: Long,
        recipients: List<String>,
        body: String,
        request: SendRequest,
        settings: AppSettings,
        subscriptionId: Int,
    ): Outcome<SendResult> {
        if (!hasMobileData()) return Outcome.Failure(AppError.NoMobileData)

        val budget = mmsTransport.maxMessageSizeBytes(subscriptionId)
        val encoded = attachmentEncoder.encode(body.takeIf { it.isNotEmpty() }, request.attachments, budget)
        val parts = when (encoded) {
            is MmsAttachmentEncoder.Result.Success -> encoded.parts
            is MmsAttachmentEncoder.Result.TooLarge ->
                return Outcome.Failure(AppError.MessageTooLarge(encoded.limitBytes))

            is MmsAttachmentEncoder.Result.Unreadable ->
                return Outcome.Failure(AppError.AttachmentUnreadable(encoded.uri))
        }

        val nowSeconds = System.currentTimeMillis() / 1000L
        val messageUri = mmsProvider.insertMessage(
            threadId = threadId,
            messageBox = Telephony.Mms.MESSAGE_BOX_OUTBOX,
            messageType = PduHeaders.MESSAGE_TYPE_SEND_REQ,
            dateSeconds = nowSeconds,
            dateSentSeconds = nowSeconds,
            subject = request.subject,
            subjectCharset = PduCharsets.UTF_8,
            read = true,
            subscriptionId = subscriptionId,
            transactionId = null,
            messageId = null,
            contentLocation = null,
            expirySeconds = 0L,
            messageSize = parts.sumOf { it.data.size }.toLong(),
            from = null,
            to = recipients,
            parts = parts,
        ) ?: return Outcome.Failure(AppError.Unexpected())

        val systemId = ContentUris.parseId(messageUri)
        val localId = syncRepository.syncSingleMms(systemId) ?: 0L
        if (localId > 0) applyReplyLink(localId, request)

        val handle = mmsTransport.send(
            recipients = recipients,
            parts = parts,
            subject = request.subject,
            subscriptionId = subscriptionId,
            requestDeliveryReport = settings.deliveryReports,
            requestReadReport = settings.sendMmsReadReports,
            resultIntentFactory = { transactionId ->
                Intent(context, MmsStatusReceiver::class.java).apply {
                    action = MmsStatusReceiver.ACTION_MMS_SENT
                    putExtra(MmsStatusReceiver.EXTRA_SYSTEM_ID, systemId)
                    putExtra(MmsStatusReceiver.EXTRA_LOCAL_MESSAGE_ID, localId)
                    putExtra(MmsStatusReceiver.EXTRA_THREAD_ID, threadId)
                    putExtra(MmsStatusReceiver.EXTRA_TRANSACTION_ID, transactionId)
                }
            },
        )

        if (handle.isFailure) {
            mmsProvider.updateMessageBox(systemId, Telephony.Mms.MESSAGE_BOX_FAILED)
            syncRepository.syncSingleMms(systemId)
            return Outcome.Failure(AppError.SendFailed(ERROR_CODE_LOCAL_FAILURE))
        }
        return Outcome.Success(SendResult(threadId, listOfNotNull(localId.takeIf { it > 0 }), usedMms = true))
    }

    private suspend fun applyReplyLink(localMessageId: Long, request: SendRequest) {
        val replyTo = request.replyToMessageId ?: return
        runCatching {
            syncRepository.linkReply(localMessageId, replyTo, request.replyToSnippet)
        }.onFailure { Log.d(TAG, "Could not store the reply link", it) }
    }

    /**
     * Applies the reply quote when the user has that setting on. Reaction fallbacks are already a
     * complete sentence and are never quoted.
     */
    private fun composeBody(request: SendRequest, settings: AppSettings): String {
        if (request.isReactionFallback) return request.body
        val snippet = request.replyToSnippet
        return if (settings.quoteWhenReplying && !snippet.isNullOrBlank() && request.body.isNotBlank()) {
            QuotedReply.format(snippet, request.body)
        } else {
            request.body
        }
    }

    private suspend fun resolveSubscription(requested: Int, settings: AppSettings): Int {
        if (requested >= 0) return requested
        val configured = settings.defaultSubscriptionId
        if (configured >= 0) return configured
        val available = sims.availableSims()
        return if (available.size == 1) available.first().subscriptionId else sims.defaultSmsSubscriptionId()
    }

    /** MMS travels over mobile data; Wi-Fi does not help, so this check has to be specific. */
    private fun hasMobileData(): Boolean {
        val manager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
            ?: return true
        return try {
            manager.allNetworks.any { network ->
                val capabilities = manager.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            }
        } catch (error: SecurityException) {
            true
        }
    }

    private companion object {
        const val TAG = "MessageSender"

        /** Beyond this many concatenated parts an MMS is cheaper and more reliable. */
        const val MAX_PARTS_BEFORE_MMS = 4

        /** Used when the platform refuses the request before any result code exists. */
        const val ERROR_CODE_LOCAL_FAILURE = -1
    }
}
