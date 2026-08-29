package app.pingu.messages.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.entity.AttachmentEntity
import app.pingu.messages.data.local.entity.BlockedNumberEntity
import app.pingu.messages.data.local.entity.ConversationEntity
import app.pingu.messages.data.local.entity.ConversationMetadataEntity
import app.pingu.messages.data.local.entity.DraftAttachmentEntity
import app.pingu.messages.data.local.entity.DraftEntity
import app.pingu.messages.data.local.entity.MessageEntity
import app.pingu.messages.data.local.entity.ReactionEntity
import app.pingu.messages.data.local.entity.ScheduledMessageEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The database, exercised through the DAOs the app actually uses.
 *
 * The queries here encode behaviour the UI depends on and that is easy to break silently: pinned
 * threads sort first, archived and blocked threads stay out of the inbox, deleting a message takes
 * its attachments with it, and the app's own metadata survives a re-sync of the mirror.
 */
@RunWith(AndroidJUnit4::class)
class PinguDatabaseTest {

    private lateinit var database: PinguDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PinguDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun conversation(id: Long, timestamp: Long, unread: Int = 0) = ConversationEntity(
        threadId = id,
        addresses = "+44770090012$id",
        snippet = "snippet $id",
        snippetIsOutgoing = false,
        snippetHasAttachment = false,
        lastMessageTimestamp = timestamp,
        unreadCount = unread,
        messageCount = 1,
        systemArchived = false,
        recipientCount = 1,
        lastSyncedAt = timestamp,
    )

    @Test
    fun inboxIsOrderedByRecencyWithPinnedThreadsFirst() = runTest {
        val dao = database.conversationDao()
        dao.upsertAll(
            listOf(conversation(1, 1_000), conversation(2, 3_000), conversation(3, 2_000)),
        )
        listOf(1L, 2L, 3L).forEach { dao.insertMetadataIfMissing(ConversationMetadataEntity(it)) }
        dao.updateMetadata(3L) { it.copy(pinned = true, pinnedAt = 5_000) }

        val inbox = dao.observeInbox(null, 50).first()
        assertThat(inbox.map { it.threadId }).containsExactly(3L, 2L, 1L).inOrder()
    }

    @Test
    fun archivedAndBlockedThreadsLeaveTheInbox() = runTest {
        val dao = database.conversationDao()
        dao.upsertAll(listOf(conversation(1, 1_000), conversation(2, 2_000), conversation(3, 3_000)))
        listOf(1L, 2L, 3L).forEach { dao.insertMetadataIfMissing(ConversationMetadataEntity(it)) }
        dao.updateMetadata(2L) { it.copy(archived = true) }
        dao.updateMetadata(3L) { it.copy(blocked = true) }

        assertThat(dao.observeInbox(null, 50).first().map { it.threadId }).containsExactly(1L)
        assertThat(dao.observeArchived().first().map { it.threadId }).containsExactly(2L)
        assertThat(dao.observeBlockedAndSpam().first().map { it.threadId }).containsExactly(3L)
    }

    @Test
    fun conversationMetadataSurvivesAMirrorRefresh() = runTest {
        val dao = database.conversationDao()
        dao.upsert(conversation(1, 1_000))
        dao.insertMetadataIfMissing(ConversationMetadataEntity(1L))
        dao.updateMetadata(1L) { it.copy(pinned = true, muted = true, customTitle = "Book club") }

        // A later sync replaces the mirrored row wholesale.
        dao.upsert(conversation(1, 9_000))

        val row = dao.getConversation(1L)
        assertThat(row?.lastMessageTimestamp).isEqualTo(9_000)
        assertThat(row?.pinned).isTrue()
        assertThat(row?.muted).isTrue()
        assertThat(row?.customTitle).isEqualTo("Book club")
    }

    @Test
    fun draftsAppearOnTheConversationRow() = runTest {
        val dao = database.conversationDao()
        dao.upsert(conversation(1, 1_000))
        dao.insertMetadataIfMissing(ConversationMetadataEntity(1L))
        database.draftDao().replace(
            draft = DraftEntity(threadId = 1L, text = "unsent", updatedAt = 1_000),
            attachments = listOf(
                DraftAttachmentEntity(threadId = 1L, uri = "content://a", mimeType = "image/png"),
            ),
        )

        val row = dao.getConversation(1L)
        assertThat(row?.draftText).isEqualTo("unsent")
        assertThat(row?.draftAttachmentCount).isEqualTo(1)
    }

    @Test
    fun messagesReadBackNewestFirstAndAreWindowed() = runTest {
        val dao = database.messageDao()
        repeat(10) { index ->
            dao.insert(message(id = index.toLong() + 1, timestamp = index * 1_000L))
        }

        val window = dao.observeRecent(1L, 3).first()
        assertThat(window).hasSize(3)
        assertThat(window.map { it.timestamp })
            .containsExactly(9_000L, 8_000L, 7_000L)
            .inOrder()
        assertThat(dao.countInThread(1L)).isEqualTo(10)
    }

    @Test
    fun theMirroredUpdateKeepsAppOnlyColumns() = runTest {
        val dao = database.messageDao()
        val localId = dao.insert(message(id = 1, timestamp = 1_000))
        dao.updateReplyLink(localId, replyTo = 99L, snippet = "original")

        val updated = dao.updateMirroredColumns(
            transport = "SMS",
            systemId = 1L,
            threadId = 1L,
            address = "+447700900123",
            body = "edited by the provider",
            subject = null,
            timestamp = 2_000,
            sentTimestamp = 2_000,
            isOutgoing = false,
            isRead = true,
            status = "RECEIVED",
            errorCode = 0,
            subscriptionId = -1,
            sizeBytes = 0,
            contentLocation = null,
            transactionId = null,
            expiryTimestamp = 0,
            hasAttachments = false,
        )

        assertThat(updated).isEqualTo(1)
        val row = dao.getById(localId)
        assertThat(row?.body).isEqualTo("edited by the provider")
        assertThat(row?.replyToMessageId).isEqualTo(99L)
        assertThat(row?.replyToSnippet).isEqualTo("original")
    }

    @Test
    fun deletingAMessageRemovesItsAttachmentsAndReactions() = runTest {
        val messageDao = database.messageDao()
        val id = messageDao.insert(message(id = 1, timestamp = 1_000))
        database.attachmentDao().insertAll(
            listOf(AttachmentEntity(messageId = id, uri = "content://a", mimeType = "image/png")),
        )
        database.reactionDao().upsert(
            ReactionEntity(messageId = id, emoji = "👍", authorKey = "", timestamp = 1_000),
        )

        messageDao.deleteByIds(listOf(id))

        assertThat(database.attachmentDao().forMessage(id)).isEmpty()
        assertThat(database.reactionDao().forMessages(listOf(id))).isEmpty()
    }

    @Test
    fun oneReactionPerAuthorPerMessage() = runTest {
        val id = database.messageDao().insert(message(id = 1, timestamp = 1_000))
        val dao = database.reactionDao()
        dao.upsert(ReactionEntity(messageId = id, emoji = "👍", authorKey = "", timestamp = 1))
        dao.upsert(ReactionEntity(messageId = id, emoji = "❤️", authorKey = "", timestamp = 2))

        val reactions = dao.forMessages(listOf(id))
        assertThat(reactions).hasSize(1)
        assertThat(reactions.first().emoji).isEqualTo("❤️")
    }

    @Test
    fun searchFindsMessagesAndSkipsBlockedThreads() = runTest {
        val conversations = database.conversationDao()
        conversations.upsertAll(listOf(conversation(1, 1_000), conversation(2, 2_000)))
        conversations.insertMetadataIfMissing(ConversationMetadataEntity(1L))
        conversations.insertMetadataIfMissing(ConversationMetadataEntity(2L, blocked = true))

        val dao = database.messageDao()
        dao.insert(message(id = 1, timestamp = 1_000, body = "dinner at eight"))
        dao.insert(message(id = 2, timestamp = 2_000, threadId = 2L, body = "dinner offer"))

        val hits = dao.searchMessages("dinner", limit = 10, offset = 0)
        assertThat(hits.map { it.threadId }).containsExactly(1L)
    }

    @Test
    fun blockedNumbersAreDeduplicatedByTheirMatchKey() = runTest {
        val dao = database.blockedNumberDao()
        dao.insert(
            BlockedNumberEntity(
                address = "+447700900123",
                matchKey = "700900123",
                origin = "MANUAL",
                blockedAt = 1,
            ),
        )
        dao.insert(
            BlockedNumberEntity(
                address = "07700900123",
                matchKey = "700900123",
                origin = "MANUAL",
                blockedAt = 2,
            ),
        )
        assertThat(dao.getAll()).hasSize(1)
        assertThat(dao.isBlocked("700900123")).isTrue()
    }

    @Test
    fun scheduledMessagesBecomeDueAtTheirTime() = runTest {
        val dao = database.scheduledMessageDao()
        dao.insert(
            ScheduledMessageEntity(
                threadId = 1L,
                recipients = "+447700900123",
                body = "later",
                scheduledAt = 5_000,
                createdAt = 0,
                state = "PENDING",
            ),
        )
        assertThat(dao.due(4_999)).isEmpty()
        assertThat(dao.due(5_000)).hasSize(1)
    }

    private fun message(
        id: Long,
        timestamp: Long,
        threadId: Long = 1L,
        body: String = "message $id",
    ) = MessageEntity(
        threadId = threadId,
        transport = "SMS",
        systemId = id,
        address = "+447700900123",
        body = body,
        timestamp = timestamp,
        isOutgoing = false,
        isRead = true,
        status = "RECEIVED",
    )
}
