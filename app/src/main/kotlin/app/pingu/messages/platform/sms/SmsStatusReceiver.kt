package app.pingu.messages.platform.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import app.pingu.messages.PinguApplication
import app.pingu.messages.di.AppContainer
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.platform.BroadcastScope

/**
 * Results of an outgoing text message.
 *
 * Two different callbacks arrive here. The **sent** callback says whether the network accepted the
 * message and carries a result code that explains a failure. The **delivered** callback only exists
 * when delivery reports were requested, and carries the raw status PDU from the network; its status
 * byte is the only honest source of "delivered", which is why the app never infers delivery from a
 * successful send.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PinguApplication).container
        val messageUri = intent.getStringExtra(EXTRA_MESSAGE_URI)?.let(Uri::parse)
        val localMessageId = intent.getLongExtra(EXTRA_LOCAL_MESSAGE_ID, 0L)
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0L)
        val resultCode = this.resultCode

        when (intent.action) {
            ACTION_SENT -> {
                val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
                val partCount = intent.getIntExtra(EXTRA_PART_COUNT, 1)
                BroadcastScope.launch(this, TAG) {
                    handleSent(
                        context = context,
                        container = container,
                        messageUri = messageUri,
                        localMessageId = localMessageId,
                        threadId = threadId,
                        resultCode = resultCode,
                        isLastPart = partIndex == partCount - 1,
                    )
                }
            }

            ACTION_DELIVERED -> {
                val status = extractDeliveryStatus(intent)
                BroadcastScope.launch(this, TAG) {
                    handleDelivered(container, messageUri, localMessageId, status)
                }
            }
        }
    }

    private suspend fun handleSent(
        context: Context,
        container: AppContainer,
        messageUri: Uri?,
        localMessageId: Long,
        threadId: Long,
        resultCode: Int,
        isLastPart: Boolean,
    ) {
        if (resultCode == Activity.RESULT_OK) {
            if (!isLastPart) return
            messageUri?.let { container.smsProviderDataSource.markSent(it) }
            if (localMessageId > 0) {
                container.messageRepository.setStatus(localMessageId, MessageStatus.SENT)
            }
            messageUri?.let { container.syncRepository.syncSingleSms(it) }
        } else {
            messageUri?.let { container.smsProviderDataSource.markFailed(it, resultCode) }
            if (localMessageId > 0) {
                container.messageRepository.setStatus(localMessageId, MessageStatus.FAILED, resultCode)
            }
            messageUri?.let { container.syncRepository.syncSingleSms(it) }
            val conversation = container.conversationRepository.getConversation(threadId)
            container.messageNotifier.notifySendFailure(
                threadId = threadId,
                conversationTitle = conversation?.title.orEmpty(),
            )
        }
        container.widgetUpdater.requestUpdate()
    }

    private suspend fun handleDelivered(
        container: AppContainer,
        messageUri: Uri?,
        localMessageId: Long,
        status: Int?,
    ) {
        val delivered = status != null && status < STATUS_FAILED_THRESHOLD
        messageUri?.let {
            container.smsProviderDataSource.updateDeliveryStatus(
                it,
                if (delivered) Telephony.Sms.STATUS_COMPLETE else Telephony.Sms.STATUS_FAILED,
            )
        }
        if (localMessageId > 0) {
            container.messageRepository.setStatus(
                localMessageId,
                if (delivered) MessageStatus.DELIVERED else MessageStatus.FAILED,
            )
        }
        messageUri?.let { container.syncRepository.syncSingleSms(it) }
    }

    /**
     * Reads the status byte out of the delivery report PDU.
     *
     * Values below 0x20 mean the message reached the handset; 0x20-0x3F is a temporary error and
     * 0x40 and above is permanent. Returning null when the PDU is missing keeps the app from
     * claiming a delivery it cannot prove.
     */
    private fun extractDeliveryStatus(intent: Intent): Int? {
        val pdu = intent.getByteArrayExtra(EXTRA_PDU) ?: return null
        val format = intent.getStringExtra(EXTRA_FORMAT)
        return runCatching {
            @Suppress("DEPRECATION")
            val message = if (format != null) {
                SmsMessage.createFromPdu(pdu, format)
            } else {
                SmsMessage.createFromPdu(pdu)
            }
            message?.status
        }.getOrNull()
    }

    companion object {
        private const val TAG = "SmsStatusReceiver"

        const val ACTION_SENT = "app.pingu.messages.action.SMS_SENT"
        const val ACTION_DELIVERED = "app.pingu.messages.action.SMS_DELIVERED"

        const val EXTRA_MESSAGE_URI = "message_uri"
        const val EXTRA_LOCAL_MESSAGE_ID = "local_message_id"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_PART_COUNT = "part_count"

        private const val EXTRA_PDU = "pdu"
        private const val EXTRA_FORMAT = "format"

        /** GSM 03.40 TP-Status: below this the message was delivered. */
        private const val STATUS_FAILED_THRESHOLD = 0x20
    }
}
