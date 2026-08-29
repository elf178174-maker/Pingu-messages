package app.pingu.messages.platform.scheduling

import android.util.Log
import app.pingu.messages.data.repository.ScheduledMessageRepository
import app.pingu.messages.domain.model.AppError
import app.pingu.messages.domain.model.Outcome
import app.pingu.messages.domain.model.ScheduledFailureReason
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

    /**
     * A stable key for why the send failed.
     *
     * The reason is written into the database and read back by the scheduled-messages screen, which
     * turns it into a sentence in the user's language. Storing the sentence itself would freeze the
     * language it was written in at the moment of failure.
     */
    private fun describe(error: AppError): String = when (error) {
        AppError.NotDefaultSmsApp -> ScheduledFailureReason.NOT_DEFAULT_SMS_APP
        AppError.NoSim -> ScheduledFailureReason.NO_SIM
        AppError.NoService -> ScheduledFailureReason.NO_SERVICE
        AppError.NoMobileData -> ScheduledFailureReason.NO_MOBILE_DATA
        is AppError.MessageTooLarge -> ScheduledFailureReason.TOO_LARGE
        is AppError.PermissionRequired -> ScheduledFailureReason.PERMISSION_REQUIRED
        is AppError.AttachmentUnreadable -> ScheduledFailureReason.ATTACHMENT_UNREADABLE
        AppError.NoHandlingApp -> ScheduledFailureReason.NO_HANDLING_APP
        is AppError.SendFailed -> ScheduledFailureReason.SEND_FAILED
        AppError.RecordingFailed -> ScheduledFailureReason.UNEXPECTED
        AppError.LocationUnavailable -> ScheduledFailureReason.UNEXPECTED
        is AppError.Unexpected -> ScheduledFailureReason.UNEXPECTED
    }

    private companion object {
        const val TAG = "ScheduledDispatcher"
    }
}
