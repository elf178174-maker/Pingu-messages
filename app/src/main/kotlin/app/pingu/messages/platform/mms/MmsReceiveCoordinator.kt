package app.pingu.messages.platform.mms

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import app.pingu.messages.data.mms.pdu.DecodedPdu
import app.pingu.messages.data.mms.pdu.MmsPart
import app.pingu.messages.data.mms.pdu.PduCharsets
import app.pingu.messages.data.mms.pdu.PduHeaders
import app.pingu.messages.data.mms.pdu.PduParser
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.data.repository.ConversationRepository
import app.pingu.messages.data.repository.MessageRepository
import app.pingu.messages.data.repository.SyncRepository
import app.pingu.messages.data.telephony.MmsProviderDataSource
import app.pingu.messages.data.telephony.SimDataSource
import app.pingu.messages.data.telephony.ThreadsDataSource
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.platform.messaging.IncomingMessageHandler
import app.pingu.messages.platform.notification.MessageNotifier
import java.io.File
import kotlinx.coroutines.flow.first

/**
 * The MMS receive and send lifecycle.
 *
 * An incoming multimedia message goes through four steps, each of which can fail on its own and
 * each of which the user can see:
 *
 *  1. a notification PDU arrives and is stored, so the conversation shows something immediately;
 *  2. the body is downloaded through the platform, either automatically or when the user asks;
 *  3. the downloaded PDU is parsed and written to the telephony provider as a real message;
 *  4. the carrier is acknowledged so it stops re-notifying.
 *
 * Keeping the notification row until step 3 succeeds is what makes "Download failed - try again"
 * possible rather than the message simply disappearing.
 */
class MmsReceiveCoordinator(
    private val context: Context,
    private val mmsProvider: MmsProviderDataSource,
    private val threads: ThreadsDataSource,
    private val transport: MmsTransport,
    private val sims: SimDataSource,
    private val settings: SettingsStore,
    private val syncRepository: SyncRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val incoming: IncomingMessageHandler,
    private val notifier: MessageNotifier,
) {

    /** Step 1: store the notification and decide whether to fetch the message. */
    suspend fun onNotification(pdu: DecodedPdu, subscriptionId: Int) {
        val from = PduParser.stripAddressType(pdu.from).orEmpty()
        val transactionId = pdu.transactionId
        val contentLocation = pdu.contentLocation

        if (from.isBlank() || contentLocation.isNullOrBlank() || transactionId.isNullOrBlank()) {
            Log.w(TAG, "Incomplete MMS notification; nothing can be fetched")
            return
        }

        val threadId = threads.getOrCreateThreadId(listOf(from)) ?: 0L
        val nowSeconds = System.currentTimeMillis() / 1000L

        val uri = mmsProvider.insertMessage(
            threadId = threadId,
            messageBox = Telephony.Mms.MESSAGE_BOX_INBOX,
            messageType = PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND,
            dateSeconds = if (pdu.date > 0) pdu.date else nowSeconds,
            dateSentSeconds = pdu.date,
            subject = pdu.subject,
            subjectCharset = pdu.subjectCharsetMib,
            read = false,
            subscriptionId = subscriptionId,
            transactionId = transactionId,
            messageId = pdu.messageId,
            contentLocation = contentLocation,
            expirySeconds = pdu.expiry,
            messageSize = pdu.messageSize,
            from = from,
            to = emptyList(),
            parts = emptyList(),
        )
        if (uri == null) {
            Log.e(TAG, "The MMS provider refused the notification")
            return
        }
        val systemId = ContentUris.parseId(uri)
        syncRepository.syncSingleMms(systemId)

        if (incoming.isBlocked(from)) {
            conversations.setBlocked(listOf(threadId), true)
            transport.notifyDeclined(transactionId, subscriptionId, deferred = false)
            return
        }

        val current = settings.settings.first()
        val roamingBlocks = sims.isRoaming() && !current.autoDownloadMmsWhileRoaming
        if (!current.autoDownloadMms || roamingBlocks) {
            // Tell the carrier to hold the message; the user can still fetch it from the thread.
            transport.notifyDeclined(transactionId, subscriptionId, deferred = true)
            notifyPending(threadId)
            return
        }

        startDownload(systemId, transactionId, contentLocation, subscriptionId)
    }

    /** Step 2: ask the platform to fetch the message body. Also used by the manual retry button. */
    suspend fun startDownload(
        systemId: Long,
        transactionId: String?,
        contentLocation: String,
        subscriptionId: Int,
    ) {
        markStatus(systemId, MessageStatus.DOWNLOADING)
        val result = transport.download(contentLocation, subscriptionId) { downloadPath ->
            Intent(context, MmsStatusReceiver::class.java).apply {
                action = MmsStatusReceiver.ACTION_MMS_DOWNLOADED
                putExtra(MmsStatusReceiver.EXTRA_SYSTEM_ID, systemId)
                putExtra(MmsStatusReceiver.EXTRA_TRANSACTION_ID, transactionId)
                putExtra(MmsStatusReceiver.EXTRA_DOWNLOAD_PATH, downloadPath)
                putExtra(MmsStatusReceiver.EXTRA_SUBSCRIPTION_ID, subscriptionId)
            }
        }
        if (result.isFailure) markStatus(systemId, MessageStatus.DOWNLOAD_FAILED)
    }

    /** Step 3 and 4: parse the downloaded PDU, store it, and acknowledge the carrier. */
    suspend fun onDownloadResult(
        notificationSystemId: Long,
        transactionId: String?,
        pduPath: String?,
        subscriptionId: Int,
        succeeded: Boolean,
    ) {
        if (!succeeded || pduPath == null) {
            markStatus(notificationSystemId, MessageStatus.DOWNLOAD_FAILED)
            mmsProvider.updateRetrieveStatus(notificationSystemId, RETRIEVE_STATUS_ERROR)
            return
        }

        val pdu = transport.parseDownloadedPdu(pduPath)
        transport.cleanUp(File(pduPath), null)

        if (pdu == null || !pdu.isRetrieveConf) {
            markStatus(notificationSystemId, MessageStatus.DOWNLOAD_FAILED)
            mmsProvider.updateRetrieveStatus(notificationSystemId, RETRIEVE_STATUS_ERROR)
            return
        }

        val notification = mmsProvider.queryById(notificationSystemId)
        val from = PduParser.stripAddressType(pdu.from)
            ?: mmsProvider.senderAddress(notificationSystemId)
            ?: return
        val threadId = notification?.threadId
            ?: threads.getOrCreateThreadId(listOf(from))
            ?: 0L

        val parts = pdu.parts.filterNot { it.isSmil }
        val uri = mmsProvider.insertMessage(
            threadId = threadId,
            messageBox = Telephony.Mms.MESSAGE_BOX_INBOX,
            messageType = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF,
            dateSeconds = if (pdu.date > 0) pdu.date else System.currentTimeMillis() / 1000L,
            dateSentSeconds = pdu.date,
            subject = pdu.subject,
            subjectCharset = pdu.subjectCharsetMib,
            read = false,
            subscriptionId = subscriptionId,
            transactionId = transactionId ?: notification?.transactionId,
            messageId = pdu.messageId,
            contentLocation = null,
            expirySeconds = 0L,
            messageSize = parts.sumOf { it.data.size }.toLong(),
            from = from,
            to = pdu.to.mapNotNull(PduParser::stripAddressType),
            parts = parts.map(::normalizePart),
        )

        if (uri == null) {
            markStatus(notificationSystemId, MessageStatus.DOWNLOAD_FAILED)
            return
        }

        // The notification row has served its purpose; keeping it would duplicate the message.
        mmsProvider.delete(notificationSystemId)

        val newSystemId = ContentUris.parseId(uri)
        incoming.onMmsStored(newSystemId)

        transactionId?.let { transport.acknowledge(it, subscriptionId) }

        val current = settings.settings.first()
        if (current.sendMmsReadReports && pdu.readReport == PduHeaders.VALUE_YES) {
            pdu.messageId?.let { transport.sendReadReport(it, from, subscriptionId) }
        }
    }

    /** Result of an outgoing multimedia message. */
    suspend fun onSendResult(
        systemId: Long,
        localMessageId: Long,
        threadId: Long,
        succeeded: Boolean,
        resultCode: Int,
    ) {
        if (succeeded) {
            mmsProvider.updateMessageBox(systemId, Telephony.Mms.MESSAGE_BOX_SENT)
            if (localMessageId > 0) messages.setStatus(localMessageId, MessageStatus.SENT)
        } else {
            mmsProvider.updateMessageBox(systemId, Telephony.Mms.MESSAGE_BOX_FAILED)
            if (localMessageId > 0) {
                messages.setStatus(localMessageId, MessageStatus.FAILED, resultCode)
            }
            val conversation = conversations.getConversation(threadId)
            notifier.notifySendFailure(threadId, conversation?.title.orEmpty())
        }
        syncRepository.syncSingleMms(systemId)
    }

    /** An M-Delivery.ind: the recipient's phone received the message. */
    suspend fun onDeliveryReport(pdu: DecodedPdu) {
        val messageId = pdu.messageId ?: return
        val row = mmsProvider.queryByMessageId(messageId) ?: return
        val delivered = pdu.status == PduHeaders.STATUS_RETRIEVED
        mmsProvider.updateStatus(row.id, pdu.status ?: PduHeaders.STATUS_UNRECOGNIZED)
        syncRepository.syncSingleMms(row.id)
        val local = mmsLocalId(row.id) ?: return
        messages.setStatus(local, if (delivered) MessageStatus.DELIVERED else MessageStatus.FAILED)
    }

    /** An M-Read-Orig.ind: the recipient opened the message. MMS is the only transport with this. */
    suspend fun onReadReport(pdu: DecodedPdu) {
        val messageId = pdu.messageId ?: return
        val row = mmsProvider.queryByMessageId(messageId) ?: return
        val local = mmsLocalId(row.id) ?: return
        messages.setStatus(local, MessageStatus.READ)
    }

    private suspend fun mmsLocalId(systemId: Long): Long? =
        syncRepository.syncSingleMms(systemId)

    private suspend fun markStatus(systemId: Long, status: MessageStatus) {
        val localId = syncRepository.syncSingleMms(systemId) ?: return
        messages.setStatus(localId, status)
    }

    private suspend fun notifyPending(threadId: Long) {
        val conversation = conversations.getConversation(threadId) ?: return
        if (!notifier.shouldNotify(conversation)) return
        val current = settings.settings.first()
        val newest = messages.getMessages(messages.unreadIncomingIds(threadId))
        if (newest.isEmpty()) return
        notifier.notifyConversation(
            conversation = conversation,
            messages = newest.sortedBy { it.timestamp },
            privacy = current.notificationPrivacy,
            vibrate = current.notificationVibrate,
            bubblesEnabled = current.conversationBubbles,
        )
    }

    /** Text parts are stored with their charset; binary parts keep their bytes. */
    private fun normalizePart(part: MmsPart): MmsPart =
        if (part.isText && part.charsetMib == PduCharsets.ANY_CHARSET) {
            part.copy(charsetMib = PduCharsets.UTF_8)
        } else {
            part
        }

    private companion object {
        const val TAG = "MmsReceive"

        /** `RETRIEVE_STATUS` value meaning the fetch failed and may be retried. */
        const val RETRIEVE_STATUS_ERROR = 0xC1
    }
}
