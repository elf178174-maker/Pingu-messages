package app.pingu.messages.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.pingu.messages.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/** Newest-first search hit, joined with just enough conversation context to render a result row. */
data class MessageSearchRow(
    val messageId: Long,
    val threadId: Long,
    val body: String?,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val address: String?,
    val addresses: String,
    val customTitle: String?,
)

@Dao
interface MessageDao {

    /**
     * The newest [limit] messages of a thread, newest first.
     *
     * The conversation screen renders a reversed list, so "newest first" is also the order the
     * `LazyColumn` consumes; growing [limit] is what "load older messages" does. A thread with
     * 10,000 messages therefore costs one indexed lookup of the visible window, never a full read.
     */
    @Query(
        "SELECT * FROM messages WHERE threadId = :threadId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit",
    )
    fun observeRecent(threadId: Long, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp DESC, id DESC")
    suspend fun getAllForThread(threadId: Long): List<MessageEntity>

    /** A one-shot read of the newest messages of a thread. */
    @Query(
        "SELECT * FROM messages WHERE threadId = :threadId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit",
    )
    suspend fun recentOnce(threadId: Long, limit: Int): List<MessageEntity>

    /** The newest message of a thread; the source of the conversation-list snippet. */
    @Query(
        "SELECT * FROM messages WHERE threadId = :threadId " +
            "ORDER BY timestamp DESC, id DESC LIMIT 1",
    )
    suspend fun newestInThread(threadId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE transport = :transport AND systemId = :systemId")
    suspend fun getBySystemId(transport: String, systemId: Long): MessageEntity?

    /**
     * How many messages in the thread are newer than the given one. Used to size the window when
     * jumping to a search result so the target is loaded together with its surroundings.
     */
    @Query(
        "SELECT COUNT(*) FROM messages WHERE threadId = :threadId AND " +
            "(timestamp > :timestamp OR (timestamp = :timestamp AND id > :messageId))",
    )
    suspend fun countNewerThan(threadId: Long, timestamp: Long, messageId: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId")
    suspend fun countInThread(threadId: Long): Int

    @Query("SELECT * FROM messages WHERE threadId = :threadId AND isRead = 0 AND isOutgoing = 0")
    suspend fun unreadIncoming(threadId: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId AND isRead = 0 AND isOutgoing = 0")
    suspend fun unreadCount(threadId: Long): Int

    @Query("UPDATE messages SET isRead = 1 WHERE threadId = :threadId AND isRead = 0")
    suspend fun markThreadRead(threadId: Long)

    @Query("UPDATE messages SET isRead = 0 WHERE id = :messageId")
    suspend fun markUnread(messageId: Long)

    @Query("UPDATE messages SET status = :status, errorCode = :errorCode WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorCode: Int)

    @Query("UPDATE messages SET systemId = :systemId WHERE id = :id")
    suspend fun updateSystemId(id: Long, systemId: Long)

    @Query("UPDATE messages SET replyToMessageId = :replyTo, replyToSnippet = :snippet WHERE id = :id")
    suspend fun updateReplyLink(id: Long, replyTo: Long?, snippet: String?)

    @Query("SELECT * FROM messages WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<String>): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: String): MessageEntity?

    /**
     * Updates only the columns the telephony provider owns, leaving the app-only columns
     * (reply links, cached attachment flag) untouched.
     */
    @Query(
        "UPDATE messages SET threadId = :threadId, address = :address, body = :body, " +
            "subject = :subject, timestamp = :timestamp, sentTimestamp = :sentTimestamp, " +
            "isOutgoing = :isOutgoing, isRead = :isRead, status = :status, errorCode = :errorCode, " +
            "subscriptionId = :subscriptionId, sizeBytes = :sizeBytes, " +
            "contentLocation = :contentLocation, transactionId = :transactionId, " +
            "expiryTimestamp = :expiryTimestamp, hasAttachments = :hasAttachments " +
            "WHERE transport = :transport AND systemId = :systemId",
    )
    suspend fun updateMirroredColumns(
        transport: String,
        systemId: Long,
        threadId: Long,
        address: String?,
        body: String?,
        subject: String?,
        timestamp: Long,
        sentTimestamp: Long,
        isOutgoing: Boolean,
        isRead: Boolean,
        status: String,
        errorCode: Int,
        subscriptionId: Int,
        sizeBytes: Long,
        contentLocation: String?,
        transactionId: String?,
        expiryTimestamp: Long,
        hasAttachments: Boolean,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>): List<Long>

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("SELECT id FROM messages WHERE threadId = :threadId AND transport = :transport")
    suspend fun idsForThread(threadId: Long, transport: String): List<Long>

    @Query("SELECT systemId FROM messages WHERE transport = :transport")
    suspend fun systemIdsFor(transport: String): List<Long>

    @Query("DELETE FROM messages WHERE transport = :transport AND systemId IN (:systemIds)")
    suspend fun deleteBySystemIds(transport: String, systemIds: List<Long>)

    @Query(
        "SELECT m.id AS messageId, m.threadId AS threadId, m.body AS body, m.timestamp AS timestamp, " +
            "m.isOutgoing AS isOutgoing, m.address AS address, " +
            "IFNULL(c.addresses, '') AS addresses, meta.customTitle AS customTitle " +
            "FROM messages m " +
            "LEFT JOIN conversations c ON c.threadId = m.threadId " +
            "LEFT JOIN conversation_metadata meta ON meta.threadId = m.threadId " +
            "WHERE m.body LIKE '%' || :query || '%' AND IFNULL(meta.blocked, 0) = 0 " +
            "ORDER BY m.timestamp DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchMessages(query: String, limit: Int, offset: Int): List<MessageSearchRow>

    @Query(
        "SELECT COUNT(*) FROM messages m " +
            "LEFT JOIN conversation_metadata meta ON meta.threadId = m.threadId " +
            "WHERE m.body LIKE '%' || :query || '%' AND IFNULL(meta.blocked, 0) = 0",
    )
    suspend fun countSearchMessages(query: String): Int

    @Query(
        "SELECT * FROM messages WHERE threadId = :threadId AND hasAttachments = 1 " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun messagesWithAttachments(threadId: Long, limit: Int): List<MessageEntity>
}
