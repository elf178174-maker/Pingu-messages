package app.pingu.messages.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A message queued for a future time.
 *
 * Persisted rather than held in memory so the queue survives a reboot, a force stop and an app
 * update; [SystemEventReceiver] re-arms the alarms from this table.
 */
@Entity(
    tableName = "scheduled_messages",
    indices = [
        Index(value = ["scheduledAt"]),
        Index(value = ["state"]),
        Index(value = ["threadId"]),
    ],
)
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Zero until the thread exists; resolved when the message is finally sent. */
    val threadId: Long,
    /** Space-separated recipient addresses. */
    val recipients: String,
    val body: String,
    val subject: String? = null,
    val scheduledAt: Long,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "-1")
    val subscriptionId: Int = -1,
    val state: String,
    val failureReason: String? = null,
    @ColumnInfo(defaultValue = "0")
    val attempts: Int = 0,
)

@Entity(
    tableName = "scheduled_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduledMessageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["scheduledMessageId"])],
)
data class ScheduledAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val scheduledMessageId: Long,
    val uri: String,
    val mimeType: String,
    val fileName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val sizeBytes: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val width: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val height: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val durationMillis: Long = 0L,
    val extra: String? = null,
)
