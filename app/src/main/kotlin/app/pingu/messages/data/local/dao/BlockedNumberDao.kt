package app.pingu.messages.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.pingu.messages.data.local.entity.BlockedNumberEntity
import app.pingu.messages.data.local.entity.SpamKeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {

    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    fun observeAll(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers")
    suspend fun getAll(): List<BlockedNumberEntity>

    @Query("SELECT matchKey FROM blocked_numbers")
    suspend fun allMatchKeys(): List<String>

    @Query("SELECT matchKey FROM blocked_numbers")
    fun observeMatchKeys(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE matchKey = :matchKey)")
    suspend fun isBlocked(matchKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE matchKey = :matchKey")
    suspend fun deleteByMatchKey(matchKey: String)

    @Query("UPDATE blocked_numbers SET syncedToSystem = :synced WHERE matchKey = :matchKey")
    suspend fun setSyncedToSystem(matchKey: String, synced: Boolean)

    // ---- Spam keywords --------------------------------------------------------------------

    @Query("SELECT * FROM spam_keywords ORDER BY keyword ASC")
    fun observeKeywords(): Flow<List<SpamKeywordEntity>>

    @Query("SELECT keyword FROM spam_keywords")
    suspend fun keywords(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeyword(keyword: SpamKeywordEntity)

    @Query("DELETE FROM spam_keywords WHERE id = :id")
    suspend fun deleteKeyword(id: Long)
}
