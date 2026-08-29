package app.pingu.messages.platform.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import app.pingu.messages.PinguApplication
import app.pingu.messages.platform.BroadcastScope

/**
 * Receives incoming text messages.
 *
 * Only the default SMS app gets `SMS_DELIVER`, and with that privilege comes the obligation: the
 * platform does **not** write the message to the SMS provider, this app must. If it does not, the
 * message exists nowhere and is lost the moment the broadcast returns.
 *
 * A multipart message arrives as several PDUs in one broadcast and is reassembled here in order.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            ?.filterNotNull()
            .orEmpty()
        if (parts.isEmpty()) return

        val address = parts.first().displayOriginatingAddress ?: parts.first().originatingAddress
        if (address.isNullOrBlank()) return

        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = System.currentTimeMillis()
        val sentTimestamp = parts.first().timestampMillis
        val subscriptionId = subscriptionIdOf(intent)

        val container = (context.applicationContext as PinguApplication).container

        BroadcastScope.launch(this, TAG) {
            val threadId = container.threadsDataSource.getOrCreateThreadId(listOf(address))
            val uri = container.smsProviderDataSource.insertReceived(
                address = address,
                body = body,
                timestampMillis = timestamp,
                sentTimestampMillis = sentTimestamp,
                subscriptionId = subscriptionId,
                read = false,
                threadId = threadId,
            )
            if (uri == null) {
                Log.e(TAG, "The SMS provider refused the message; it cannot be stored")
                return@launch
            }
            container.incomingMessageHandler.onSmsStored(uri)
        }
    }

    /**
     * The SIM that received the message. Android has used two different extra keys for this over
     * the years and some builds provide neither, so both are tried and -1 means "unknown", which
     * downstream treats as the default subscription.
     */
    private fun subscriptionIdOf(intent: Intent): Int {
        val modern = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
        if (modern >= 0) return modern
        return intent.getIntExtra(EXTRA_SUBSCRIPTION_LEGACY, -1)
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"

        /** The key older platform builds use for the receiving subscription. */
        const val EXTRA_SUBSCRIPTION_LEGACY = "subscription"
    }
}
