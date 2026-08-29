package app.pingu.messages.domain.model

/** Lifecycle of a message queued for a future time. */
enum class ScheduledMessageState {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED,
}

/**
 * A message the user asked to be sent later.
 *
 * Scheduling is backed by [android.app.AlarmManager] with a WorkManager sweep as a safety net, and
 * the queue is re-armed after a reboot, a time change and an app update. Nothing is kept in memory
 * only, so a scheduled message survives the process being killed.
 */
data class ScheduledMessage(
    val id: Long = 0L,
    val threadId: Long,
    val recipients: List<String>,
    val body: String,
    val subject: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val scheduledAt: Long,
    val createdAt: Long = 0L,
    val subscriptionId: Int = -1,
    val state: ScheduledMessageState = ScheduledMessageState.PENDING,
    /** Human readable reason shown next to a failed entry. */
    val failureReason: String? = null,
    val attempts: Int = 0,
) {
    val isPending: Boolean get() = state == ScheduledMessageState.PENDING

    val requiresMms: Boolean get() = attachments.isNotEmpty() || !subject.isNullOrBlank()
}
