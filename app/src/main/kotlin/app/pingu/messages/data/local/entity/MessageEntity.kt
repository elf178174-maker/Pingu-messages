package app.pingu.messages.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirror of a row in `content://sms` or `content://mms`, plus the app-only fields SMS cannot carry.
 *
 * The pair (`transport`, `systemId`) is unique and is what the sync upserts on, so the local [id]
 * stays stable and app-only columns such as [replyToMessageId] are never clobbered by a re-sync.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["transport", "systemId"], unique = true),
        Index(value = ["threadId", "timestamp"]),
        Index(value = ["threadId", "isRead"]),
        Index(value = ["status"]),
        Index(value = ["timestamp"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val threadId: Long,
    /** "SMS" or "MMS". */
    val transport: String,
    /** `_id` in the corresponding provider table. */
    val systemId: Long,
    val address: String?,
    val body: String?,
    val subject: String? = null,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0")
    val sentTimestamp: Long = 0L,
    val isOutgoing: Boolean,
    val isRead: Boolean,
    val status: String,
    @ColumnInfo(defaultValue = "0")
    val errorCode: Int = 0,
    @ColumnInfo(defaultValue = "-1")
    val subscriptionId: Int = -1,
    @ColumnInfo(defaultValue = "0")
    val sizeBytes: Long = 0L,
    /** MMS content location, needed to (re)download a pending message. */
    val contentLocation: String? = null,
    /** MMS transaction id, used to correlate delivery and read reports. */
    val transactionId: String? = null,
    /** Expiry of an undownloaded MMS as reported by the carrier. */
    @ColumnInfo(defaultValue = "0")
    val expiryTimestamp: Long = 0L,

    // App-only columns below. The telephony sync never writes these.
    val replyToMessageId: Long? = null,
    val replyToSnippet: String? = null,
    @ColumnInfo(defaultValue = "0")
    val hasAttachments: Boolean = false,
)
