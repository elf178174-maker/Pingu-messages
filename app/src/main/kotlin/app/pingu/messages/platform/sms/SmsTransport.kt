package app.pingu.messages.platform.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import app.pingu.messages.data.telephony.SimDataSource
import app.pingu.messages.platform.PendingIntents

/**
 * Hands text messages to the radio.
 *
 * Long messages are split by the platform into concatenated parts. Each part gets its own result
 * callback so a partial failure is reported honestly: if any part fails the message is marked as
 * not sent, and it is only marked sent once the last part has been accepted.
 *
 * Delivery reports are requested only when the user asked for them, because many carriers charge
 * for each one.
 */
class SmsTransport(
    private val context: Context,
    private val sims: SimDataSource,
) {

    /**
     * @param messageUri the row in the system SMS provider that this send corresponds to.
     * @return failure only when the platform refused the request outright; everything else is
     * reported asynchronously to [SmsStatusReceiver].
     */
    fun send(
        destination: String,
        body: String,
        subscriptionId: Int,
        messageUri: Uri?,
        localMessageId: Long,
        threadId: Long,
        requestDeliveryReport: Boolean,
    ): Result<Unit> = runCatching {
        val manager = sims.smsManagerFor(subscriptionId)
        val parts = manager.divideMessage(body)

        if (parts.size <= 1) {
            manager.sendTextMessage(
                destination,
                null,
                body,
                sentIntent(messageUri, localMessageId, threadId, 0, 1),
                if (requestDeliveryReport) {
                    deliveredIntent(messageUri, localMessageId, threadId)
                } else {
                    null
                },
            )
        } else {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            val deliveredIntents = ArrayList<PendingIntent?>(parts.size)
            parts.indices.forEach { index ->
                sentIntents.add(sentIntent(messageUri, localMessageId, threadId, index, parts.size))
                deliveredIntents.add(
                    if (requestDeliveryReport && index == parts.lastIndex) {
                        deliveredIntent(messageUri, localMessageId, threadId)
                    } else {
                        null
                    },
                )
            }
            manager.sendMultipartTextMessage(
                destination,
                null,
                parts,
                sentIntents,
                deliveredIntents,
            )
        }
    }.onFailure { error ->
        Log.w(TAG, "Could not hand the message to the radio", error)
    }

    /** How many concatenated parts a body will occupy on this subscription. */
    fun partCount(body: String, subscriptionId: Int): Int = try {
        sims.smsManagerFor(subscriptionId).divideMessage(body).size
    } catch (error: Exception) {
        1
    }

    private fun sentIntent(
        messageUri: Uri?,
        localMessageId: Long,
        threadId: Long,
        partIndex: Int,
        partCount: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        PendingIntents.nextRequestCode(),
        Intent(context, SmsStatusReceiver::class.java).apply {
            action = SmsStatusReceiver.ACTION_SENT
            putExtra(SmsStatusReceiver.EXTRA_MESSAGE_URI, messageUri?.toString())
            putExtra(SmsStatusReceiver.EXTRA_LOCAL_MESSAGE_ID, localMessageId)
            putExtra(SmsStatusReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsStatusReceiver.EXTRA_PART_COUNT, partCount)
        },
        PendingIntents.mutable,
    )

    private fun deliveredIntent(
        messageUri: Uri?,
        localMessageId: Long,
        threadId: Long,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        PendingIntents.nextRequestCode(),
        Intent(context, SmsStatusReceiver::class.java).apply {
            action = SmsStatusReceiver.ACTION_DELIVERED
            putExtra(SmsStatusReceiver.EXTRA_MESSAGE_URI, messageUri?.toString())
            putExtra(SmsStatusReceiver.EXTRA_LOCAL_MESSAGE_ID, localMessageId)
            putExtra(SmsStatusReceiver.EXTRA_THREAD_ID, threadId)
        },
        PendingIntents.mutable,
    )

    companion object {
        private const val TAG = "SmsTransport"

        /** Maps a platform result code to a sentence a person can act on. */
        fun describeFailure(resultCode: Int): FailureReason = when (resultCode) {
            SmsManager.RESULT_ERROR_NO_SERVICE -> FailureReason.NO_SERVICE
            SmsManager.RESULT_ERROR_RADIO_OFF -> FailureReason.RADIO_OFF
            SmsManager.RESULT_ERROR_NULL_PDU -> FailureReason.NULL_PDU
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> FailureReason.LIMIT_EXCEEDED
            else -> FailureReason.GENERIC
        }
    }

    enum class FailureReason { NO_SERVICE, RADIO_OFF, NULL_PDU, LIMIT_EXCEEDED, GENERIC }
}
