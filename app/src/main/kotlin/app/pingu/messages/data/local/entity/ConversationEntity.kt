package app.pingu.messages.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirror of one row of the system thread table.
 *
 * Everything here is owned by the telephony provider and is replaced wholesale on every sync. User
 * decisions (pin, mute, archive, block) live in [ConversationMetadataEntity] so a re-sync can never
 * lose them.
 *
 * [addresses] keeps the participant list denormalised as a space-separated string, exactly as the
 * provider stores it. Resolving those to contacts happens once per sync in memory rather than with
 * a join per row, which is what keeps a list of a thousand threads scrolling smoothly.
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["lastMessageTimestamp"]),
        Index(value = ["unreadCount"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey
    val threadId: Long,
    val addresses: String,
    val snippet: String,
    val snippetIsOutgoing: Boolean,
    @ColumnInfo(defaultValue = "0")
    val snippetHasAttachment: Boolean,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val messageCount: Int,
    /** `Telephony.Threads.ARCHIVED`, which the platform also honours for other messaging apps. */
    val systemArchived: Boolean,
    /** Recipient count as reported by the provider; group threads have more than one. */
    val recipientCount: Int,
    val lastSyncedAt: Long,
)
