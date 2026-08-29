package app.pingu.messages.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.pingu.messages.data.local.entity.ScheduledAttachmentEntity
import app.pingu.messages.data.local.entity.ScheduledMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScheduledMessageDao {

    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledAt ASC")
    abstract fun observeAll(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE threadId = :threadId ORDER BY scheduledAt ASC")
    abstract fun observeForThread(threadId: Long): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    abstract suspend fun get(id: Long): ScheduledMessageEntity?

    @Query("SELECT * FROM scheduled_messages WHERE state = 'PENDING' ORDER BY scheduledAt ASC")
    abstract suspend fun pending(): List<ScheduledMessageEntity>

    @Query(
        "SELECT * FROM scheduled_messages WHERE state = 'PENDING' AND scheduledAt <= :now " +
            "ORDER BY scheduledAt ASC",
    )
    abstract suspend fun due(now: Long): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_attachments WHERE scheduledMessageId = :id ORDER BY id ASC")
    abstract suspend fun attachments(id: Long): List<ScheduledAttachmentEntity>

    @Insert
    abstract suspend fun insert(message: ScheduledMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAttachments(attachments: List<ScheduledAttachmentEntity>)

    @Transaction
    open suspend fun insertWithAttachments(
        message: ScheduledMessageEntity,
        attachments: List<ScheduledAttachmentEntity>,
    ): Long {
        val id = insert(message)
        if (attachments.isNotEmpty()) {
            insertAttachments(attachments.map { it.copy(scheduledMessageId = id) })
        }
        return id
    }

    @Query("UPDATE scheduled_messages SET state = :state, failureReason = :reason WHERE id = :id")
    abstract suspend fun updateState(id: Long, state: String, reason: String?)

    @Query("UPDATE scheduled_messages SET scheduledAt = :scheduledAt WHERE id = :id")
    abstract suspend fun reschedule(id: Long, scheduledAt: Long)

    @Query("UPDATE scheduled_messages SET attempts = attempts + 1 WHERE id = :id")
    abstract suspend fun incrementAttempts(id: Long)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM scheduled_messages WHERE state IN ('SENT', 'CANCELLED') AND scheduledAt < :before")
    abstract suspend fun purgeCompletedBefore(before: Long)

    @Query("SELECT COUNT(*) FROM scheduled_messages WHERE state = 'PENDING'")
    abstract fun observePendingCount(): Flow<Int>
}
