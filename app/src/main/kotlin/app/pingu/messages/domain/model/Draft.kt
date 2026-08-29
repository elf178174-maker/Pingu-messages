package app.pingu.messages.domain.model

/**
 * A composer state that survives leaving the screen, the process being killed and the device
 * rebooting. Drafts for existing threads are also mirrored into the system provider so other
 * messaging apps see them.
 */
data class Draft(
    val threadId: Long,
    val text: String = "",
    val subject: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val replyToMessageId: Long? = null,
    val replyToSnippet: String? = null,
    val subscriptionId: Int = -1,
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = text.isBlank() && subject.isNullOrBlank() && attachments.isEmpty()
}
