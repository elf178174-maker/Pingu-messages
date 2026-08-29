package app.pingu.messages.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import app.pingu.messages.PinguApplication
import app.pingu.messages.R
import app.pingu.messages.domain.model.Outcome
import app.pingu.messages.platform.BroadcastScope
import app.pingu.messages.platform.messaging.SendRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Notification actions: reply, mark as read, dismiss.
 *
 * The reply action really sends the message. It goes through the same [SendRequest] path as the
 * composer, so it is written to the provider, gets a delivery callback and shows in the thread -
 * there is no separate, weaker code path for notification replies.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0L)
        if (threadId <= 0L) return
        val container = (context.applicationContext as PinguApplication).container

        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) return

                BroadcastScope.launch(this, TAG) {
                    val conversation = container.conversationRepository.getConversation(threadId)
                    if (conversation == null) {
                        toast(context, context.getString(R.string.notification_reply_failed))
                        return@launch
                    }
                    val outcome = container.messageSender.send(
                        SendRequest(
                            threadId = threadId,
                            recipients = conversation.recipients.map { it.address },
                            body = text,
                            subscriptionId = conversation.subscriptionId,
                        ),
                    )
                    when (outcome) {
                        is Outcome.Success -> {
                            container.conversationRepository.markRead(threadId)
                            container.messageNotifier.cancelConversation(threadId)
                        }

                        is Outcome.Failure ->
                            toast(context, context.getString(R.string.notification_reply_failed))
                    }
                    container.widgetUpdater.requestUpdate()
                }
            }

            ACTION_MARK_READ -> BroadcastScope.launch(this, TAG) {
                container.conversationRepository.markRead(threadId)
                container.messageNotifier.cancelConversation(threadId)
                container.widgetUpdater.requestUpdate()
            }

            ACTION_DISMISS -> BroadcastScope.launch(this, TAG) {
                // Swiping a message notification away marks it seen but deliberately not read:
                // the unread badge is the user's own to clear.
                container.conversationRepository.ensureMetadata(threadId)
            }
        }
    }

    private suspend fun toast(context: Context, message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "NotificationAction"

        const val ACTION_REPLY = "app.pingu.messages.action.NOTIFICATION_REPLY"
        const val ACTION_MARK_READ = "app.pingu.messages.action.NOTIFICATION_MARK_READ"
        const val ACTION_DISMISS = "app.pingu.messages.action.NOTIFICATION_DISMISS"

        const val EXTRA_THREAD_ID = "thread_id"
        const val KEY_REPLY_TEXT = "reply_text"
    }
}
