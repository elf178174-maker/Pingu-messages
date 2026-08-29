package app.pingu.messages.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined folder for organising conversations (Work, Family, Deliveries...).
 *
 * Purely local organisation on top of the system threads; a conversation belongs to at most one
 * folder and keeps working normally when it belongs to none.
 */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["position"])],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val colorSlot: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
    val createdAt: Long,
)
