package app.pingu.messages.platform.scheduling

import android.util.Log
import app.pingu.messages.data.repository.ScheduledMessageRepository
import app.pingu.messages.domain.model.AppError
import app.pingu.messages.domain.model.Outcome
import app.pingu.messages.domain.model.ScheduledMessage
import app.pingu.messages.platform.messaging.MessageSender
import app.pingu.messages.platform.messaging.SendRequest
import app.pingu.messages.platform.notification.MessageNotifier

/**
 * Sends a scheduled message when its time arrives.
 *
 * A failure here is visible rather than silent: the row is marked failed with a reason the
 * scheduled-messages screen shows, and a notification points at the conversation, because a message
 * the user believed was sent quietly failing is the worst possible outcome.
 */
class ScheduledMessageDispatcher(
    private val repository: ScheduledMessageRepository,
    private val sender: MessageSender,
    private val notifier: MessageNotifier,
    private val scheduler: ScheduledMessageScheduler,
) {

    suspend fun dispatch(id: Long) {
        val message = repository.get(id) ?: return
        if (!message.isPending) return
        send(message)
    }

    /** Sends everything whose time has passed; used by the sweep worker and after a reboot. */
    suspend fun dispatchDue(now: Long = System.currentTimeMillis()) {
        repository.due(now).forEach { queued ->
            val full = repository.get(queued.id) ?: return@forEach
            send(full)
        }
    }

    private suspend fun send(message: ScheduledMessage) {
        repository.markSending(message.id)
        val outcome = sender.send(
            SendRequest(
                threadId = message.threadId.takeIf { it > 0 },
                recipients = message.recipients,
                body = message.body,
                subject = message.subject,
                attachments = message.attachments,
                subscriptionId = message.subscriptionId,
            ),
        )
        when (outcome) {
            is Outcome.Success -> {
                repository.markSent(message.id)
                scheduler.cancel(message.id)
            }

            is Outcome.Failure -> {
                val reason = describe(outcome.error)
                repository.markFailed(message.id, reason)
                notifier.notifySendFailure(message.threadId, message.recipients.joinToString(", "))
                Log.w(TAG, "Scheduled message ${message.id} failed: $reason")
            }
        }
    }

    /** Short, specific sentences; the screen shows these next to the failed entry. */
    private fun describe(error: AppError): String = when (error) {
        AppError.NotDefaultSmsApp -> "Pingu Messages is no longer the default SMS app"
        AppError.NoSim -> "No SIM card"
        AppError.NoService -> "No mobile service"
        AppError.NoMobileData -> "Mobile data was unavailable for MMS"
        is AppError.MessageTooLarge -> "Too large for the carrier"
        is AppError.PermissionRequired -> "A permission was withdrawn"
        is AppError.AttachmentUnreadable -> "An attachment could no longer be read"
        AppError.NoHandlingApp -> "No app could handle the request"
        is AppError.SendFailed -> "The network rejected the message"
        is AppError.Unexpected -> "Unexpected failure"
    }

    private companion object {
        const val TAG = "ScheduledDispatcher"
    }
}
