package app.pingu.messages.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.pingu.messages.data.local.entity.AttachmentEntity

/** Attachment row joined with the thread it belongs to, for the media grid and search. */
data class AttachmentWithContext(
    val id: Long,
    val messageId: Long,
    val threadId: Long,
    val uri: String,
    val mimeType: String,
    val fileName: String?,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val extra: String?,
    val timestamp: Long,
    val isOutgoing: Boolean,
)

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY id ASC")
    suspend fun forMessage(messageId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE messageId IN (:messageIds) ORDER BY id ASC")
    suspend fun forMessages(messageIds: List<Long>): List<AttachmentEntity>

    @Query(
        "SELECT a.id AS id, a.messageId AS messageId, m.threadId AS threadId, a.uri AS uri, " +
            "a.mimeType AS mimeType, a.fileName AS fileName, a.sizeBytes AS sizeBytes, " +
            "a.width AS width, a.height AS height, a.durationMillis AS durationMillis, " +
            "a.extra AS extra, m.timestamp AS timestamp, m.isOutgoing AS isOutgoing " +
            "FROM attachments a JOIN messages m ON m.id = a.messageId " +
            "WHERE m.threadId = :threadId ORDER BY m.timestamp DESC, a.id DESC LIMIT :limit",
    )
    suspend fun forThread(threadId: Long, limit: Int): List<AttachmentWithContext>

    @Query(
        "SELECT a.id AS id, a.messageId AS messageId, m.threadId AS threadId, a.uri AS uri, " +
            "a.mimeType AS mimeType, a.fileName AS fileName, a.sizeBytes AS sizeBytes, " +
            "a.width AS width, a.height AS height, a.durationMillis AS durationMillis, " +
            "a.extra AS extra, m.timestamp AS timestamp, m.isOutgoing AS isOutgoing " +
            "FROM attachments a JOIN messages m ON m.id = a.messageId " +
            "LEFT JOIN conversation_metadata meta ON meta.threadId = m.threadId " +
            "WHERE IFNULL(a.fileName, '') LIKE '%' || :query || '%' " +
            "AND IFNULL(meta.blocked, 0) = 0 " +
            "ORDER BY m.timestamp DESC LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int): List<AttachmentWithContext>

    @Query("SELECT IFNULL(SUM(sizeBytes), 0) FROM attachments")
    suspend fun totalBytes(): Long

    @Query(
        "SELECT a.uri FROM attachments a JOIN messages m ON m.id = a.messageId " +
            "WHERE m.timestamp < :olderThan",
    )
    suspend fun urisOlderThan(olderThan: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: Long)

    @Query("DELETE FROM attachments WHERE messageId IN (:messageIds)")
    suspend fun deleteForMessages(messageIds: List<Long>)
}
