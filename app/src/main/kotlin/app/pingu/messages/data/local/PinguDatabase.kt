package app.pingu.messages.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.pingu.messages.data.local.dao.AttachmentDao
import app.pingu.messages.data.local.dao.BlockedNumberDao
import app.pingu.messages.data.local.dao.ConversationDao
import app.pingu.messages.data.local.dao.DraftDao
import app.pingu.messages.data.local.dao.FolderDao
import app.pingu.messages.data.local.dao.MessageDao
import app.pingu.messages.data.local.dao.ReactionDao
import app.pingu.messages.data.local.dao.ScheduledMessageDao
import app.pingu.messages.data.local.entity.AttachmentEntity
import app.pingu.messages.data.local.entity.BlockedNumberEntity
import app.pingu.messages.data.local.entity.ConversationEntity
import app.pingu.messages.data.local.entity.ConversationMetadataEntity
import app.pingu.messages.data.local.entity.DraftAttachmentEntity
import app.pingu.messages.data.local.entity.DraftEntity
import app.pingu.messages.data.local.entity.FolderEntity
import app.pingu.messages.data.local.entity.MessageEntity
import app.pingu.messages.data.local.entity.ReactionEntity
import app.pingu.messages.data.local.entity.ScheduledAttachmentEntity
import app.pingu.messages.data.local.entity.ScheduledMessageEntity
import app.pingu.messages.data.local.entity.SpamKeywordEntity

/**
 * The app's local database.
 *
 * Two kinds of data live here and they are deliberately kept in separate tables:
 *
 *  * a **mirror** of the system telephony provider (conversations, messages, attachments), which
 *    exists so the UI can query, sort and search without hitting a content provider on every
 *    frame, and which can be rebuilt from scratch at any time; and
 *  * **app-owned state** (pins, mutes, archive flags, reactions, reply links, drafts, scheduled
 *    messages, blocked numbers, folders), which exists nowhere else and must never be lost.
 *
 * Because the two are separate, a full re-sync can replace every mirrored row without touching a
 * single user decision.
 *
 * ### Migrations
 * `exportSchema` is on and the generated schema JSON is committed under `app/schemas`, so every
 * future change is reviewable in a diff. There is no `fallbackToDestructiveMigration` anywhere in
 * this file: an unhandled schema change must fail loudly in development rather than silently
 * delete a user's messages on update. See [Migrations] for how to add one.
 */
@Database(
    entities = [
        ConversationEntity::class,
        ConversationMetadataEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ReactionEntity::class,
        DraftEntity::class,
        DraftAttachmentEntity::class,
        ScheduledMessageEntity::class,
        ScheduledAttachmentEntity::class,
        BlockedNumberEntity::class,
        SpamKeywordEntity::class,
        FolderEntity::class,
    ],
    version = PinguDatabase.VERSION,
    exportSchema = true,
)
abstract class PinguDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reactionDao(): ReactionDao
    abstract fun draftDao(): DraftDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val VERSION = 1
        const val NAME = "pingu-messages.db"

        fun build(context: Context): PinguDatabase =
            Room.databaseBuilder(context.applicationContext, PinguDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
