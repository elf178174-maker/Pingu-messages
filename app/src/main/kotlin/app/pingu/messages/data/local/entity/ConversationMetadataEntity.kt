package app.pingu.messages.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * App-owned conversation state. Never touched by the telephony sync, so pinning a thread survives
 * the thread being re-read from the provider, an app update or a restore.
 */
@Entity(
    tableName = "conversation_metadata",
    indices = [
        Index(value = ["pinned"]),
        Index(value = ["archived"]),
        Index(value = ["blocked"]),
        Index(value = ["folderId"]),
    ],
)
data class ConversationMetadataEntity(
    @PrimaryKey
    val threadId: Long,
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val pinnedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val muted: Boolean = false,
    /** Epoch millis when a temporary mute lapses; zero means "until turned off". */
    @ColumnInfo(defaultValue = "0")
    val mutedUntil: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val blocked: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val spam: Boolean = false,
    val customTitle: String? = null,
    /** SIM pinned to this thread; -1 follows the global default. */
    @ColumnInfo(defaultValue = "-1")
    val subscriptionId: Int = -1,
    @ColumnInfo(defaultValue = "1")
    val notificationsEnabled: Boolean = true,
    val folderId: Long? = null,
    /** Timestamp of the newest message the user has actually seen, used for the unread divider. */
    @ColumnInfo(defaultValue = "0")
    val lastSeenTimestamp: Long = 0L,
)
