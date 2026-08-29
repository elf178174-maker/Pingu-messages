package app.pingu.messages.domain.model

/**
 * A thread, combining the system thread row with the app's own conversation metadata.
 *
 * The thread id is the telephony provider's, which is what keeps Pingu Messages interoperable:
 * threads created here are visible to any other messaging app the user later switches to.
 */
data class Conversation(
    val threadId: Long,
    val recipients: List<Recipient>,
    val snippet: String = "",
    val snippetIsOutgoing: Boolean = false,
    val snippetStatus: MessageStatus? = null,
    val snippetHasAttachment: Boolean = false,
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val messageCount: Int = 0,
    val draftText: String? = null,
    val draftHasAttachments: Boolean = false,
    val isPinned: Boolean = false,
    val pinnedAt: Long = 0L,
    val isMuted: Boolean = false,
    val mutedUntil: Long = 0L,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false,
    val isSpam: Boolean = false,
    /** User-chosen title, overriding the generated participant list. */
    val customTitle: String? = null,
    /** SIM pinned to this conversation, or -1 to follow the global default. */
    val subscriptionId: Int = -1,
    /** Notification behaviour that overrides the global setting for this thread. */
    val notificationsEnabled: Boolean = true,
) {
    val isGroup: Boolean get() = recipients.size > 1

    val hasUnread: Boolean get() = unreadCount > 0

    val hasDraft: Boolean get() = !draftText.isNullOrBlank() || draftHasAttachments

    /** Generated title when the user has not set one: contact names, or numbers as a fallback. */
    val title: String
        get() = customTitle?.takeIf { it.isNotBlank() }
            ?: recipients.joinToString(", ") { it.label }.ifBlank { "" }

    /** Whether the mute is open ended or has expired. */
    fun isMutedAt(now: Long): Boolean = isMuted && (mutedUntil == 0L || mutedUntil > now)
}
