package app.pingu.messages.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.pingu.messages.data.local.entity.ReactionEntity

@Dao
interface ReactionDao {

    @Query("SELECT * FROM reactions WHERE messageId IN (:messageIds)")
    suspend fun forMessages(messageIds: List<Long>): List<ReactionEntity>

    @Query("SELECT * FROM reactions WHERE messageId = :messageId AND authorKey = :authorKey")
    suspend fun forAuthor(messageId: Long, authorKey: String): ReactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reaction: ReactionEntity)

    @Query("DELETE FROM reactions WHERE messageId = :messageId AND authorKey = :authorKey")
    suspend fun remove(messageId: Long, authorKey: String)

    @Query("UPDATE reactions SET transmitted = 1 WHERE id = :id")
    suspend fun markTransmitted(id: Long)
}
