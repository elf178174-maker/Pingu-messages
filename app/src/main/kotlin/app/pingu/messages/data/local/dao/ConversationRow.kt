package app.pingu.messages.data.local.dao

/**
 * Flat projection of a conversation row: the provider mirror, the app's metadata and the draft
 * state in a single query, so the list never issues a follow-up query per visible row.
 */
data class ConversationRow(
    val threadId: Long,
    val addresses: String,
    val snippet: String,
    val snippetIsOutgoing: Boolean,
    val snippetHasAttachment: Boolean,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val recipientCount: Int,
    val pinned: Boolean,
    val pinnedAt: Long,
    val muted: Boolean,
    val mutedUntil: Long,
    val archived: Boolean,
    val blocked: Boolean,
    val spam: Boolean,
    val customTitle: String?,
    val subscriptionId: Int,
    val notificationsEnabled: Boolean,
    val folderId: Long?,
    val lastSeenTimestamp: Long,
    val draftText: String?,
    val draftAttachmentCount: Int,
    val lastMessageStatus: String?,
)

/** A single unread total, used by the widget and the launcher badge. */
data class UnreadSummary(
    val conversationCount: Int,
    val messageCount: Int,
)

/** Unread count for one thread, used to carry counts across a conversation-list refresh. */
data class ThreadUnreadCount(
    val threadId: Long,
    val unreadCount: Int,
)
