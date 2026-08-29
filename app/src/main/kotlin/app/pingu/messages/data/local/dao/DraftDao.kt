package app.pingu.messages.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.pingu.messages.data.local.entity.DraftAttachmentEntity
import app.pingu.messages.data.local.entity.DraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DraftDao {

    @Query("SELECT * FROM drafts WHERE threadId = :threadId")
    abstract suspend fun get(threadId: Long): DraftEntity?

    @Query("SELECT * FROM drafts WHERE threadId = :threadId")
    abstract fun observe(threadId: Long): Flow<List<DraftEntity>>

    @Query("SELECT * FROM draft_attachments WHERE threadId = :threadId ORDER BY id ASC")
    abstract suspend fun attachments(threadId: Long): List<DraftAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(draft: DraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAttachments(attachments: List<DraftAttachmentEntity>)

    @Query("DELETE FROM draft_attachments WHERE threadId = :threadId")
    abstract suspend fun clearAttachments(threadId: Long)

    @Query("DELETE FROM drafts WHERE threadId = :threadId")
    abstract suspend fun deleteDraft(threadId: Long)

    @Transaction
    open suspend fun replace(draft: DraftEntity, attachments: List<DraftAttachmentEntity>) {
        upsert(draft)
        clearAttachments(draft.threadId)
        if (attachments.isNotEmpty()) insertAttachments(attachments)
    }

    @Transaction
    open suspend fun clear(threadId: Long) {
        clearAttachments(threadId)
        deleteDraft(threadId)
    }

    @Query("SELECT threadId FROM drafts")
    abstract suspend fun threadIdsWithDrafts(): List<Long>
}
