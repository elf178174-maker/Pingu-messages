package app.pingu.messages.platform.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import app.pingu.messages.PinguApplication
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.platform.messaging.SendRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Respond via message" from the phone app.
 *
 * When a call comes in, the dialer offers to reject it with a text. It does that by starting the
 * default SMS app's `RESPOND_VIA_MESSAGE` service with the recipient in the intent data and the
 * text in an extra. Declaring this service is one of the four requirements Android places on a
 * default SMS app, and the message it sends is a real SMS like any other.
 */
class HeadlessSmsSendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val recipients = recipientsFrom(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()

        if (recipients.isEmpty() || text.isEmpty()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val container = (application as PinguApplication).container
        scope.launch {
            try {
                container.messageSender.send(
                    SendRequest(recipients = recipients, body = text),
                )
            } catch (error: Exception) {
                Log.e(TAG, "Quick response could not be sent", error)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    /**
     * The recipient list is in the intent data, in the scheme-specific part of an `sms:` URI, and
     * may hold several numbers separated by semicolons or commas.
     */
    private fun recipientsFrom(intent: Intent): List<String> {
        val uri = intent.data ?: return emptyList()
        val body = uri.schemeSpecificPart ?: return emptyList()
        return PhoneNumbers.splitRecipients(body)
    }

    private companion object {
        const val TAG = "HeadlessSmsSend"
    }
}
