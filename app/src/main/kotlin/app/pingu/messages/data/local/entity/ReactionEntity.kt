package app.pingu.messages.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A reaction attached to a message.
 *
 * One reaction per author per message: the unique index enforces that, so tapping a second emoji
 * replaces the first exactly as it does in every other messenger.
 */
@Entity(
    tableName = "reactions",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["messageId", "authorKey"], unique = true),
    ],
)
data class ReactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val messageId: Long,
    val emoji: String,
    /** Empty string for the local user, otherwise the normalised sender key. */
    val authorKey: String,
    val authorAddress: String? = null,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0")
    val transmitted: Boolean = false,
)
