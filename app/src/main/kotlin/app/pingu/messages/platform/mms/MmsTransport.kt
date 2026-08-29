package app.pingu.messages.platform.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import app.pingu.messages.data.mms.pdu.DecodedPdu
import app.pingu.messages.data.mms.pdu.MmsPart
import app.pingu.messages.data.mms.pdu.PduComposer
import app.pingu.messages.data.mms.pdu.PduHeaders
import app.pingu.messages.data.mms.pdu.PduParser
import app.pingu.messages.data.telephony.SimDataSource
import app.pingu.messages.platform.PendingIntents
import app.pingu.messages.platform.media.AppFileStore
import java.io.File
import java.util.UUID

/**
 * The MMS transport: hands PDUs to the platform and gets them back.
 *
 * Third-party apps cannot open a carrier MMS connection themselves; what the platform offers, and
 * what this class uses, is [SmsManager.sendMultimediaMessage] and
 * [SmsManager.downloadMultimediaMessage]. Those take care of the APN, the MMS proxy and the HTTP
 * exchange. The app is responsible for everything either side of that: building a valid M-Send.req,
 * parsing the M-Retrieve.conf that comes back, storing both in the telephony provider, and
 * acknowledging the message so the carrier stops re-sending the notification.
 *
 * The PDU is exchanged through a file in the app's cache, exposed as a `content://` URI with a
 * temporary grant to the platform's phone process. That is the documented mechanism; there is no
 * way to pass bytes directly.
 */
class MmsTransport(
    private val context: Context,
    private val sims: SimDataSource,
    private val fileStore: AppFileStore,
) {

    /** Everything the send path needs to report a result later. */
    data class SendHandle(val transactionId: String, val pduFile: File, val contentUri: Uri)

    /**
     * The carrier's maximum message size in bytes, from the carrier configuration when it is
     * available and a conservative default otherwise.
     */
    fun maxMessageSizeBytes(subscriptionId: Int): Int {
        val manager = sims.smsManagerFor(subscriptionId)
        return try {
            val config: Bundle? = manager.carrierConfigValues
            val value = config?.getInt(CARRIER_CONFIG_MAX_MESSAGE_SIZE, 0) ?: 0
            if (value > 0) value else DEFAULT_MAX_MESSAGE_SIZE
        } catch (error: Exception) {
            DEFAULT_MAX_MESSAGE_SIZE
        }
    }

    fun isGroupMmsEnabled(subscriptionId: Int): Boolean {
        val manager = sims.smsManagerFor(subscriptionId)
        return try {
            manager.carrierConfigValues?.getBoolean(CARRIER_CONFIG_GROUP_MMS, true) ?: true
        } catch (error: Exception) {
            true
        }
    }

    /**
     * Sends a multimedia message.
     *
     * @param resultIntentFactory builds the broadcast that reports the outcome; it is given the
     * transaction id so the receiver can find the message again.
     */
    fun send(
        recipients: List<String>,
        parts: List<MmsPart>,
        subject: String?,
        subscriptionId: Int,
        requestDeliveryReport: Boolean,
        requestReadReport: Boolean,
        resultIntentFactory: (transactionId: String) -> Intent,
    ): Result<SendHandle> {
        val transactionId = newTransactionId()
        return runCatching {
            val pdu = PduComposer.composeSendRequest(
                transactionId = transactionId,
                recipients = recipients,
                parts = parts,
                subject = subject,
                requestDeliveryReport = requestDeliveryReport,
                requestReadReport = requestReadReport,
            )
            val file = writePdu(pdu, "send")
            val uri = fileStore.uriFor(file)
            fileStore.grantTo(AppFileStore.PLATFORM_PHONE_PACKAGE, uri)

            val sentIntent = PendingIntent.getBroadcast(
                context,
                PendingIntents.nextRequestCode(),
                resultIntentFactory(transactionId),
                PendingIntents.mutable,
            )
            sims.smsManagerFor(subscriptionId)
                .sendMultimediaMessage(context, uri, null, null, sentIntent)
            SendHandle(transactionId, file, uri)
        }.onFailure { error ->
            Log.w(TAG, "sendMultimediaMessage failed", error)
        }
    }

    /**
     * Asks the platform to fetch a message from the carrier.
     *
     * @param locationUrl the content location from the notification.
     */
    fun download(
        locationUrl: String,
        subscriptionId: Int,
        resultIntentFactory: (downloadFile: String) -> Intent,
    ): Result<File> = runCatching {
        val file = fileStore.createCacheFile(AppFileStore.DIR_MMS, ".pdu")
        file.createNewFile()
        val uri = fileStore.uriFor(file)
        fileStore.grantTo(AppFileStore.PLATFORM_PHONE_PACKAGE, uri, write = true)

        val downloadedIntent = PendingIntent.getBroadcast(
            context,
            PendingIntents.nextRequestCode(),
            resultIntentFactory(file.absolutePath),
            PendingIntents.mutable,
        )
        sims.smsManagerFor(subscriptionId)
            .downloadMultimediaMessage(context, locationUrl, uri, null, downloadedIntent)
        file
    }.onFailure { error ->
        Log.w(TAG, "downloadMultimediaMessage failed for $locationUrl", error)
    }

    /**
     * Acknowledges a downloaded message so the carrier stops re-notifying about it.
     *
     * Failures here are logged and swallowed: the message has already been delivered to the user,
     * and a missing acknowledgement is a carrier-side annoyance rather than a user-visible error.
     */
    fun acknowledge(transactionId: String, subscriptionId: Int) {
        runCatching {
            val pdu = PduComposer.composeAcknowledge(transactionId)
            val file = writePdu(pdu, "ack")
            val uri = fileStore.uriFor(file)
            fileStore.grantTo(AppFileStore.PLATFORM_PHONE_PACKAGE, uri)
            sims.smsManagerFor(subscriptionId)
                .sendMultimediaMessage(context, uri, null, null, null)
        }.onFailure { error ->
            Log.d(TAG, "Could not acknowledge $transactionId", error)
        }
    }

    /** Tells the carrier the app deliberately did not download a message. */
    fun notifyDeclined(transactionId: String, subscriptionId: Int, deferred: Boolean) {
        runCatching {
            val status = if (deferred) PduHeaders.STATUS_DEFERRED else PduHeaders.STATUS_REJECTED
            val pdu = PduComposer.composeNotifyResponse(transactionId, status)
            val file = writePdu(pdu, "notifyresp")
            val uri = fileStore.uriFor(file)
            fileStore.grantTo(AppFileStore.PLATFORM_PHONE_PACKAGE, uri)
            sims.smsManagerFor(subscriptionId)
                .sendMultimediaMessage(context, uri, null, null, null)
        }.onFailure { error ->
            Log.d(TAG, "Could not send a notify response for $transactionId", error)
        }
    }

    /** Sends an MMS read report, when the sender asked for one and the user allows them. */
    fun sendReadReport(messageId: String, recipient: String, subscriptionId: Int) {
        runCatching {
            val pdu = PduComposer.composeReadReport(
                messageId = messageId,
                recipient = recipient,
                readAtSeconds = System.currentTimeMillis() / 1000L,
            )
            val file = writePdu(pdu, "readrec")
            val uri = fileStore.uriFor(file)
            fileStore.grantTo(AppFileStore.PLATFORM_PHONE_PACKAGE, uri)
            sims.smsManagerFor(subscriptionId)
                .sendMultimediaMessage(context, uri, null, null, null)
        }.onFailure { error ->
            Log.d(TAG, "Could not send a read report for $messageId", error)
        }
    }

    fun parseDownloadedPdu(path: String): DecodedPdu? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { PduParser.parse(file.readBytes()) }.getOrNull()
    }

    fun cleanUp(file: File?, uri: Uri?) {
        uri?.let(fileStore::revokeFrom)
        fileStore.deleteQuietly(file)
    }

    private fun writePdu(pdu: ByteArray, prefix: String): File {
        val file = fileStore.createCacheFile(AppFileStore.DIR_MMS, "-$prefix.pdu")
        file.writeBytes(pdu)
        return file
    }

    private fun newTransactionId(): String =
        "T${UUID.randomUUID().toString().replace("-", "").take(TRANSACTION_ID_LENGTH)}"

    companion object {
        private const val TAG = "MmsTransport"

        /**
         * Conservative default when the carrier configuration is unavailable. Most carriers accept
         * at least 300 kB; sending under the real limit costs a little quality, exceeding it costs
         * the whole message.
         */
        const val DEFAULT_MAX_MESSAGE_SIZE = 300 * 1024

        private const val CARRIER_CONFIG_MAX_MESSAGE_SIZE = "maxMessageSize"
        private const val CARRIER_CONFIG_GROUP_MMS = "enableGroupMms"

        private const val TRANSACTION_ID_LENGTH = 16
    }
}
