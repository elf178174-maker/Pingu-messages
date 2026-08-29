package app.pingu.messages.platform.mms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pingu.messages.PinguApplication
import app.pingu.messages.platform.BroadcastScope

/**
 * Results of the two asynchronous MMS operations: sending and downloading.
 *
 * The platform reports both by broadcasting a PendingIntent the app supplied. For a download it
 * also fills the file we handed it with the retrieved PDU, which is then parsed and stored.
 */
class MmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PinguApplication).container
        val resultCode = this.resultCode
        val succeeded = resultCode == Activity.RESULT_OK

        when (intent.action) {
            ACTION_MMS_SENT -> {
                val systemId = intent.getLongExtra(EXTRA_SYSTEM_ID, 0L)
                val localId = intent.getLongExtra(EXTRA_LOCAL_MESSAGE_ID, 0L)
                val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0L)
                BroadcastScope.launch(this, TAG) {
                    container.mmsReceiveCoordinator.onSendResult(
                        systemId = systemId,
                        localMessageId = localId,
                        threadId = threadId,
                        succeeded = succeeded,
                        resultCode = resultCode,
                    )
                }
            }

            ACTION_MMS_DOWNLOADED -> {
                val path = intent.getStringExtra(EXTRA_DOWNLOAD_PATH)
                val systemId = intent.getLongExtra(EXTRA_SYSTEM_ID, 0L)
                val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID)
                val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
                BroadcastScope.launch(this, TAG) {
                    container.mmsReceiveCoordinator.onDownloadResult(
                        notificationSystemId = systemId,
                        transactionId = transactionId,
                        pduPath = path,
                        subscriptionId = subscriptionId,
                        succeeded = succeeded,
                    )
                }
            }

            else -> Log.d(TAG, "Unexpected MMS result action ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "MmsStatusReceiver"

        const val ACTION_MMS_SENT = "app.pingu.messages.action.MMS_SENT"
        const val ACTION_MMS_DOWNLOADED = "app.pingu.messages.action.MMS_DOWNLOADED"

        const val EXTRA_SYSTEM_ID = "system_id"
        const val EXTRA_LOCAL_MESSAGE_ID = "local_message_id"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_DOWNLOAD_PATH = "download_path"
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
    }
}
