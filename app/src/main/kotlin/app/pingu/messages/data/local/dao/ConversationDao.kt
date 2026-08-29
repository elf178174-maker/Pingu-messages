package app.pingu.messages.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.pingu.messages.data.local.entity.ConversationEntity
import app.pingu.messages.data.local.entity.ConversationMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Conversation queries.
 *
 * Every listing shares [PROJECTION] and [FROM_JOIN] so the sort order and the meaning of the
 * columns cannot drift apart between the inbox, the archive and the spam folder.
 */
@Dao
abstract class ConversationDao {

    companion object {
        const val PROJECTION = """
            SELECT
                c.threadId AS threadId,
                c.addresses AS addresses,
                c.snippet AS snippet,
                c.snippetIsOutgoing AS snippetIsOutgoing,
                c.snippetHasAttachment AS snippetHasAttachment,
                c.lastMessageTimestamp AS lastMessageTimestamp,
                c.unreadCount AS unreadCount,
                c.messageCount AS messageCount,
                c.recipientCount AS recipientCount,
                IFNULL(m.pinned, 0) AS pinned,
                IFNULL(m.pinnedAt, 0) AS pinnedAt,
                IFNULL(m.muted, 0) AS muted,
                IFNULL(m.mutedUntil, 0) AS mutedUntil,
                IFNULL(m.archived, c.systemArchived) AS archived,
                IFNULL(m.blocked, 0) AS blocked,
                IFNULL(m.spam, 0) AS spam,
                m.customTitle AS customTitle,
                IFNULL(m.subscriptionId, -1) AS subscriptionId,
                IFNULL(m.notificationsEnabled, 1) AS notificationsEnabled,
                m.folderId AS folderId,
                IFNULL(m.lastSeenTimestamp, 0) AS lastSeenTimestamp,
                d.text AS draftText,
                (SELECT COUNT(*) FROM draft_attachments da WHERE da.threadId = c.threadId)
                    AS draftAttachmentCount,
                (SELECT msg.status FROM messages msg WHERE msg.threadId = c.threadId
                    ORDER BY msg.timestamp DESC, msg.id DESC LIMIT 1) AS lastMessageStatus
        """

        const val FROM_JOIN = """
            FROM conversations c
            LEFT JOIN conversation_metadata m ON m.threadId = c.threadId
            LEFT JOIN drafts d ON d.threadId = c.threadId
        """

        /** Pinned threads first, most recently pinned at the top, then by recency. */
        const val ORDER = """
            ORDER BY pinned DESC, pinnedAt DESC, lastMessageTimestamp DESC, c.threadId DESC
        """
    }

    @Query(
        "$PROJECTION $FROM_JOIN WHERE archived = 0 AND blocked = 0 AND spam = 0 " +
            "AND (:folderId IS NULL OR folderId = :folderId) $ORDER LIMIT :limit",
    )
    abstract fun observeInbox(folderId: Long?, limit: Int): Flow<List<ConversationRow>>

    @Query("$PROJECTION $FROM_JOIN WHERE archived = 1 AND blocked = 0 AND spam = 0 $ORDER")
    abstract fun observeArchived(): Flow<List<ConversationRow>>

    @Query("$PROJECTION $FROM_JOIN WHERE blocked = 1 OR spam = 1 $ORDER")
    abstract fun observeBlockedAndSpam(): Flow<List<ConversationRow>>

    /**
     * Returns a list so Room never has to map an empty cursor onto a nullable row; the repository
     * takes the first element. A deleted thread then emits an empty list rather than failing.
     */
    @Query("$PROJECTION $FROM_JOIN WHERE c.threadId = :threadId")
    abstract fun observeConversation(threadId: Long): Flow<List<ConversationRow>>

    @Query("$PROJECTION $FROM_JOIN WHERE c.threadId = :threadId")
    abstract suspend fun getConversation(threadId: Long): ConversationRow?

    @Query("$PROJECTION $FROM_JOIN WHERE archived = 0 AND blocked = 0 AND spam = 0 $ORDER LIMIT :limit")
    abstract suspend fun recentConversations(limit: Int): List<ConversationRow>

    @Query(
        "$PROJECTION $FROM_JOIN WHERE blocked = 0 AND spam = 0 AND (" +
            "c.addresses LIKE '%' || :query || '%' OR " +
            "IFNULL(m.customTitle, '') LIKE '%' || :query || '%' OR " +
            "c.snippet LIKE '%' || :query || '%') $ORDER LIMIT :limit",
    )
    abstract suspend fun searchConversations(query: String, limit: Int): List<ConversationRow>

    @Query(
        "SELECT COUNT(*) AS conversationCount, IFNULL(SUM(c.unreadCount), 0) AS messageCount " +
            "FROM conversations c LEFT JOIN conversation_metadata m ON m.threadId = c.threadId " +
            "WHERE c.unreadCount > 0 AND IFNULL(m.blocked, 0) = 0 AND IFNULL(m.spam, 0) = 0 " +
            "AND IFNULL(m.archived, c.systemArchived) = 0",
    )
    abstract fun observeUnreadSummary(): Flow<UnreadSummary>

    @Upsert
    abstract suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Upsert
    abstract suspend fun upsert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE threadId NOT IN (:keepThreadIds)")
    abstract suspend fun deleteThreadsMissingFrom(keepThreadIds: List<Long>)

    @Query("DELETE FROM conversations WHERE threadId = :threadId")
    abstract suspend fun deleteConversation(threadId: Long)

    @Query("SELECT threadId FROM conversations")
    abstract suspend fun allThreadIds(): List<Long>

    @Query("SELECT threadId, unreadCount FROM conversations")
    abstract suspend fun unreadCounts(): List<ThreadUnreadCount>

    // ---- Metadata -------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMetadataIfMissing(metadata: ConversationMetadataEntity)

    @Query("SELECT * FROM conversation_metadata WHERE threadId = :threadId")
    abstract suspend fun getMetadata(threadId: Long): ConversationMetadataEntity?

    @Query("SELECT * FROM conversation_metadata")
    abstract suspend fun allMetadata(): List<ConversationMetadataEntity>

    @Upsert
    abstract suspend fun upsertMetadata(metadata: ConversationMetadataEntity)

    @Transaction
    open suspend fun updateMetadata(
        threadId: Long,
        transform: (ConversationMetadataEntity) -> ConversationMetadataEntity,
    ) {
        val current = getMetadata(threadId) ?: ConversationMetadataEntity(threadId = threadId)
        upsertMetadata(transform(current))
    }

    @Query("UPDATE conversation_metadata SET pinned = :pinned, pinnedAt = :pinnedAt WHERE threadId IN (:threadIds)")
    abstract suspend fun setPinned(threadIds: List<Long>, pinned: Boolean, pinnedAt: Long)

    @Query("UPDATE conversation_metadata SET archived = :archived WHERE threadId IN (:threadIds)")
    abstract suspend fun setArchived(threadIds: List<Long>, archived: Boolean)

    @Query("UPDATE conversation_metadata SET muted = :muted, mutedUntil = :until WHERE threadId IN (:threadIds)")
    abstract suspend fun setMuted(threadIds: List<Long>, muted: Boolean, until: Long)

    @Query("UPDATE conversation_metadata SET blocked = :blocked WHERE threadId IN (:threadIds)")
    abstract suspend fun setBlocked(threadIds: List<Long>, blocked: Boolean)

    @Query("UPDATE conversation_metadata SET spam = :spam WHERE threadId IN (:threadIds)")
    abstract suspend fun setSpam(threadIds: List<Long>, spam: Boolean)

    @Query("UPDATE conversation_metadata SET folderId = :folderId WHERE threadId IN (:threadIds)")
    abstract suspend fun setFolder(threadIds: List<Long>, folderId: Long?)

    @Query("UPDATE conversation_metadata SET lastSeenTimestamp = :timestamp WHERE threadId = :threadId")
    abstract suspend fun setLastSeen(threadId: Long, timestamp: Long)

    @Query("SELECT threadId FROM conversation_metadata WHERE blocked = 1 OR spam = 1")
    abstract suspend fun blockedOrSpamThreadIds(): List<Long>

    @Query("UPDATE conversations SET unreadCount = :count WHERE threadId = :threadId")
    abstract suspend fun setUnreadCount(threadId: Long, count: Int)
}
