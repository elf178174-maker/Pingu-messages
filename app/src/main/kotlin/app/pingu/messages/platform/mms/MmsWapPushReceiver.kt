package app.pingu.messages.platform.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import app.pingu.messages.PinguApplication
import app.pingu.messages.data.mms.pdu.PduHeaders
import app.pingu.messages.data.mms.pdu.PduParser
import app.pingu.messages.platform.BroadcastScope

/**
 * Receives the WAP push that announces a new multimedia message.
 *
 * The push is not the message: it is an **M-Notification.ind**, a small PDU with the sender, the
 * size, an expiry and a URL. As the default SMS app this app is responsible for storing that
 * notification, deciding whether to fetch the message, and telling the carrier what it decided.
 *
 * Auto-download is a real setting with real consequences (MMS uses mobile data and can cost money
 * while roaming), so when it is off the notification is stored and the conversation offers a
 * Download button instead of silently doing nothing.
 */
class MmsWapPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra(EXTRA_DATA) ?: return
        val subscriptionId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)

        val pdu = PduParser.parse(data)
        if (pdu == null) {
            Log.w(TAG, "Received a WAP push that is not a readable MMS PDU")
            return
        }

        val container = (context.applicationContext as PinguApplication).container
        BroadcastScope.launch(this, TAG) {
            when (pdu.messageType) {
                PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND ->
                    container.mmsReceiveCoordinator.onNotification(pdu, subscriptionId)

                PduHeaders.MESSAGE_TYPE_DELIVERY_IND ->
                    container.mmsReceiveCoordinator.onDeliveryReport(pdu)

                PduHeaders.MESSAGE_TYPE_READ_ORIG_IND ->
                    container.mmsReceiveCoordinator.onReadReport(pdu)

                else -> Log.d(TAG, "Ignoring MMS PDU of type ${pdu.messageType}")
            }
        }
    }

    private companion object {
        const val TAG = "MmsWapPushReceiver"
        const val EXTRA_DATA = "data"
    }
}
