package app.pingu.messages.data.repository

import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.contacts.ContactIndex
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.dao.ConversationRow
import app.pingu.messages.data.local.entity.ConversationMetadataEntity
import app.pingu.messages.data.telephony.MmsProviderDataSource
import app.pingu.messages.data.telephony.SmsProviderDataSource
import app.pingu.messages.data.telephony.TelephonyMapper
import app.pingu.messages.data.telephony.ThreadsDataSource
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Which slice of the conversation list to observe. */
enum class ConversationFilter { INBOX, ARCHIVED, BLOCKED_AND_SPAM }

/**
 * Conversations, as the UI sees them.
 *
 * The repository is where a database row becomes something with names and photos on it: the
 * conversation flow is combined with the contact index, so saving a contact updates every open
 * list without a database write or a re-sync.
 */
class ConversationRepository(
    private val database: PinguDatabase,
    private val contacts: ContactIndex,
    private val threads: ThreadsDataSource,
    private val sms: SmsProviderDataSource,
    private val mms: MmsProviderDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val dao get() = database.conversationDao()

    fun observe(filter: ConversationFilter, folderId: Long? = null): Flow<List<Conversation>> {
        val rows = when (filter) {
            ConversationFilter.INBOX -> dao.observeInbox(folderId, INBOX_LIMIT)
            ConversationFilter.ARCHIVED -> dao.observeArchived()
            ConversationFilter.BLOCKED_AND_SPAM -> dao.observeBlockedAndSpam()
        }
        return combine(rows, contacts.state) { list, _ -> list.map(::toConversation) }
    }

    fun observeConversation(threadId: Long): Flow<Conversation?> =
        combine(dao.observeConversation(threadId), contacts.state) { rows, _ ->
            rows.firstOrNull()?.let(::toConversation)
        }

    fun observeUnreadSummary(): Flow<Pair<Int, Int>> =
        dao.observeUnreadSummary().map { it.conversationCount to it.messageCount }

    suspend fun getConversation(threadId: Long): Conversation? = withContext(ioDispatcher) {
        dao.getConversation(threadId)?.let(::toConversation)
    }

    suspend fun recent(limit: Int): List<Conversation> = withContext(ioDispatcher) {
        dao.recentConversations(limit).map(::toConversation)
    }

    suspend fun search(query: String, limit: Int = 20): List<Conversation> =
        withContext(ioDispatcher) {
            if (query.isBlank()) return@withContext emptyList()
            dao.searchConversations(query.trim(), limit).map(::toConversation)
        }

    /** Resolves (creating if needed) the system thread for a set of recipients. */
    suspend fun threadIdFor(recipients: Collection<String>): Long? = withContext(ioDispatcher) {
        threads.getOrCreateThreadId(recipients)
    }

    // ---- Mutations ----------------------------------------------------------------------------

    suspend fun setPinned(threadIds: List<Long>, pinned: Boolean) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        threadIds.forEach { threadId ->
            dao.updateMetadata(threadId) {
                it.copy(pinned = pinned, pinnedAt = if (pinned) now else 0L)
            }
        }
    }

    suspend fun setMuted(threadIds: List<Long>, muted: Boolean, until: Long = 0L) =
        withContext(ioDispatcher) {
            threadIds.forEach { threadId ->
                dao.updateMetadata(threadId) { it.copy(muted = muted, mutedUntil = until) }
            }
        }

    suspend fun setArchived(threadIds: List<Long>, archived: Boolean) = withContext(ioDispatcher) {
        threadIds.forEach { threadId ->
            dao.updateMetadata(threadId) { it.copy(archived = archived) }
            // Mirror into the provider so other messaging apps agree; best effort by design.
            threads.setArchived(threadId, archived)
        }
    }

    suspend fun setSpam(threadIds: List<Long>, spam: Boolean) = withContext(ioDispatcher) {
        threadIds.forEach { threadId ->
            dao.updateMetadata(threadId) { it.copy(spam = spam, archived = spam || it.archived) }
        }
    }

    suspend fun setBlocked(threadIds: List<Long>, blocked: Boolean) = withContext(ioDispatcher) {
        threadIds.forEach { threadId ->
            dao.updateMetadata(threadId) { it.copy(blocked = blocked) }
        }
    }

    suspend fun setFolder(threadIds: List<Long>, folderId: Long?) = withContext(ioDispatcher) {
        threadIds.forEach { threadId -> dao.updateMetadata(threadId) { it.copy(folderId = folderId) } }
    }

    suspend fun setCustomTitle(threadId: Long, title: String?) = withContext(ioDispatcher) {
        dao.updateMetadata(threadId) { it.copy(customTitle = title?.takeIf { name -> name.isNotBlank() }) }
    }

    suspend fun setSubscription(threadId: Long, subscriptionId: Int) = withContext(ioDispatcher) {
        dao.updateMetadata(threadId) { it.copy(subscriptionId = subscriptionId) }
    }

    suspend fun setNotificationsEnabled(threadId: Long, enabled: Boolean) =
        withContext(ioDispatcher) {
            dao.updateMetadata(threadId) { it.copy(notificationsEnabled = enabled) }
        }

    suspend fun markRead(threadId: Long) = withContext(ioDispatcher) {
        database.messageDao().markThreadRead(threadId)
        dao.setUnreadCount(threadId, 0)
        dao.updateMetadata(threadId) { it.copy(lastSeenTimestamp = System.currentTimeMillis()) }
        sms.markThreadRead(threadId)
        mms.markThreadRead(threadId)
    }

    /** Marks the newest incoming message unread again, which is what "mark as unread" means. */
    suspend fun markUnread(threadId: Long) = withContext(ioDispatcher) {
        val newest = database.messageDao().newestInThread(threadId)
        if (newest != null && !newest.isOutgoing) {
            database.messageDao().markUnread(newest.id)
            if (newest.transport == TelephonyMapper.TRANSPORT_SMS) {
                sms.markUnread(newest.systemId)
            } else {
                mms.markUnread(newest.systemId)
            }
        }
        dao.setUnreadCount(threadId, database.messageDao().unreadCount(threadId))
    }

    /** Deletes a conversation everywhere: the provider, the mirror and the app's metadata. */
    suspend fun delete(threadIds: List<Long>) = withContext(ioDispatcher) {
        threadIds.forEach { threadId ->
            threads.deleteThread(threadId)
            sms.deleteThread(threadId)
            mms.deleteThread(threadId)
            database.messageDao().deleteThread(threadId)
            database.draftDao().clear(threadId)
            dao.deleteConversation(threadId)
        }
    }

    suspend fun ensureMetadata(threadId: Long) = withContext(ioDispatcher) {
        dao.insertMetadataIfMissing(ConversationMetadataEntity(threadId = threadId))
    }

    private fun toConversation(row: ConversationRow): Conversation {
        val addresses = PhoneNumbers.splitRecipients(row.addresses)
        return Conversation(
            threadId = row.threadId,
            recipients = contacts.toRecipients(addresses),
            snippet = row.snippet,
            snippetIsOutgoing = row.snippetIsOutgoing,
            snippetStatus = row.lastMessageStatus?.let { status ->
                runCatching { MessageStatus.valueOf(status) }.getOrNull()
            },
            snippetHasAttachment = row.snippetHasAttachment,
            lastMessageTimestamp = row.lastMessageTimestamp,
            unreadCount = row.unreadCount,
            messageCount = row.messageCount,
            draftText = row.draftText,
            draftHasAttachments = row.draftAttachmentCount > 0,
            isPinned = row.pinned,
            pinnedAt = row.pinnedAt,
            isMuted = row.muted,
            mutedUntil = row.mutedUntil,
            isArchived = row.archived,
            isBlocked = row.blocked,
            isSpam = row.spam,
            customTitle = row.customTitle,
            subscriptionId = row.subscriptionId,
            notificationsEnabled = row.notificationsEnabled,
        )
    }

    private companion object {
        /** More than any list a person scrolls; keeps a pathological provider from blocking the UI. */
        const val INBOX_LIMIT = 2_000
    }
}
