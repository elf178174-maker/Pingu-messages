package app.pingu.messages.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A composer draft.
 *
 * Drafts for an existing thread are also written to the system provider so that other messaging
 * apps, and the conversation list of any app the user switches to, show the same pending text.
 * Attachments cannot be represented there, so they are kept here alone.
 */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey
    val threadId: Long,
    val text: String,
    val subject: String? = null,
    val replyToMessageId: Long? = null,
    val replyToSnippet: String? = null,
    @ColumnInfo(defaultValue = "-1")
    val subscriptionId: Int = -1,
    val updatedAt: Long,
)

@Entity(
    tableName = "draft_attachments",
    foreignKeys = [
        ForeignKey(
            entity = DraftEntity::class,
            parentColumns = ["threadId"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["threadId"])],
)
data class DraftAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val threadId: Long,
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
