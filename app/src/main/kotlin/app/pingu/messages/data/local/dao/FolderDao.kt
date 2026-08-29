package app.pingu.messages.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.pingu.messages.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/** Folder together with the number of conversations filed in it. */
data class FolderWithCount(
    val id: Long,
    val name: String,
    val colorSlot: Int,
    val position: Int,
    val conversationCount: Int,
    val unreadCount: Int,
)

@Dao
interface FolderDao {

    @Query(
        "SELECT f.id AS id, f.name AS name, f.colorSlot AS colorSlot, f.position AS position, " +
            "(SELECT COUNT(*) FROM conversation_metadata m WHERE m.folderId = f.id) AS conversationCount, " +
            "(SELECT IFNULL(SUM(c.unreadCount), 0) FROM conversation_metadata m " +
            "  JOIN conversations c ON c.threadId = m.threadId WHERE m.folderId = f.id) AS unreadCount " +
            "FROM folders f ORDER BY f.position ASC, f.id ASC",
    )
    fun observeAll(): Flow<List<FolderWithCount>>

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE conversation_metadata SET folderId = NULL WHERE folderId = :id")
    suspend fun detachConversations(id: Long)
}
