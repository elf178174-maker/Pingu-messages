package app.pingu.messages.data.repository

import android.content.Context
import android.net.Uri
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.entity.ConversationEntity
import app.pingu.messages.data.local.entity.ConversationMetadataEntity
import app.pingu.messages.data.local.entity.MessageEntity
import app.pingu.messages.data.telephony.AttachmentMetadataReader
import app.pingu.messages.data.telephony.MmsProviderDataSource
import app.pingu.messages.data.telephony.MmsRow
import app.pingu.messages.data.telephony.SmsProviderDataSource
import app.pingu.messages.data.telephony.TelephonyMapper
import app.pingu.messages.data.telephony.ThreadsDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the local mirror in step with the system telephony provider.
 *
 * The strategy is deliberately not "read everything every time":
 *
 *  * [syncThreads] refreshes the conversation list. It is cheap and runs on every provider change.
 *  * [syncRecentMessages] mirrors the newest messages across all threads. It bounds the work of a
 *    first run on a phone with years of history, and is enough to render the inbox.
 *  * [syncThread] mirrors one thread in depth, and is what the conversation screen calls, including
 *    when the user scrolls back far enough to need older messages.
 *  * [syncSingleSms] and [syncSingleMms] handle exactly one message and are what the broadcast
 *    receivers use, so a newly arrived message is visible immediately without a full pass.
 *
 * A mutex serialises passes: two overlapping syncs would fight over the same rows and produce
 * duplicate work, and provider notifications arrive in bursts.
 */
class SyncRepository(
    private val context: Context,
    private val database: PinguDatabase,
    private val threads: ThreadsDataSource,
    private val sms: SmsProviderDataSource,
    private val mms: MmsProviderDataSource,
    private val metadataReader: AttachmentMetadataReader,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutex = Mutex()

    /**
     * A full pass: conversations first so the list is populated, then a bounded window of recent
     * messages so snippets, unread counts and search have something to work with.
     */
    suspend fun syncAll(messageWindow: Int = RECENT_MESSAGE_WINDOW) = withContext(ioDispatcher) {
        mutex.withLock {
            syncThreadsLocked()
            syncRecentMessagesLocked(messageWindow)
            refreshUnreadCountsLocked()
        }
    }

    suspend fun syncThreads() = withContext(ioDispatcher) {
        mutex.withLock { syncThreadsLocked() }
    }

    /** Mirrors one thread, then reconciles deletions so removed messages disappear here too. */
    suspend fun syncThread(threadId: Long, limit: Int = THREAD_MESSAGE_WINDOW) =
        withContext(ioDispatcher) {
            mutex.withLock {
                syncThreadLocked(threadId, limit)
                refreshUnreadCountLocked(threadId)
            }
        }

    suspend fun syncSingleSms(uri: Uri): Long? = withContext(ioDispatcher) {
        val row = sms.queryByUri(uri) ?: return@withContext null
        mutex.withLock {
            ensureConversation(row.threadId)
            val localId = upsertMessage(TelephonyMapper.toEntity(row))
            refreshUnreadCountLocked(row.threadId)
            refreshConversationSnippetLocked(row.threadId)
            localId
        }
    }

    suspend fun syncSingleMms(systemId: Long): Long? = withContext(ioDispatcher) {
        val row = mms.queryById(systemId) ?: return@withContext null
        mutex.withLock {
            ensureConversation(row.threadId)
            val localId = mirrorMms(row, enrichMetadata = true)
            refreshUnreadCountLocked(row.threadId)
            refreshConversationSnippetLocked(row.threadId)
            localId
        }
    }

    /** Stores the app-only reply link on a message that has just been written to the provider. */
    suspend fun linkReply(messageId: Long, replyToMessageId: Long, snippet: String?) =
        withContext(ioDispatcher) {
            database.messageDao().updateReplyLink(messageId, replyToMessageId, snippet)
        }

    // ---- Locked implementations -------------------------------------------------------------

    private suspend fun syncThreadsLocked() {
        val rows = threads.queryThreads()
        if (rows.isEmpty()) {
            // An empty provider is indistinguishable from a permission problem; leave the mirror
            // alone rather than wiping a list the user can still see.
            return
        }
        val canonical = threads.canonicalAddresses()
        val now = System.currentTimeMillis()

        val entities = rows.map { thread ->
            val recipients = threads.recipientsFor(thread, canonical)
            ConversationEntity(
                threadId = thread.id,
                addresses = recipients.joinToString(" "),
                snippet = thread.snippet,
                snippetIsOutgoing = false,
                snippetHasAttachment = thread.hasAttachment,
                lastMessageTimestamp = thread.dateMillis,
                unreadCount = 0,
                messageCount = thread.messageCount,
                systemArchived = thread.archived,
                recipientCount = recipients.size.coerceAtLeast(1),
                lastSyncedAt = now,
            )
        }

        val dao = database.conversationDao()
        // Preserve unread counts already computed from the mirror; the thread table has no count.
        val existingUnread = dao.unreadCounts().associate { it.threadId to it.unreadCount }
        dao.upsertAll(entities.map { it.copy(unreadCount = existingUnread[it.threadId] ?: 0) })
        entities.forEach { dao.insertMetadataIfMissing(ConversationMetadataEntity(threadId = it.threadId)) }

        val keep = entities.map { it.threadId }
        if (keep.isNotEmpty()) dao.deleteThreadsMissingFrom(keep)
    }

    private suspend fun syncRecentMessagesLocked(limit: Int) {
        val smsRows = sms.queryRecent(limit)
        val mmsRows = mms.queryRecent(limit / MMS_WINDOW_DIVISOR)

        smsRows.forEach { row ->
            ensureConversation(row.threadId)
            upsertMessage(TelephonyMapper.toEntity(row))
        }
        mmsRows.forEach { row ->
            ensureConversation(row.threadId)
            mirrorMms(row, enrichMetadata = false)
        }
        refreshSnippetsLocked(smsRows.map { it.threadId } + mmsRows.map { it.threadId })
    }

    private suspend fun syncThreadLocked(threadId: Long, limit: Int) {
        ensureConversation(threadId)
        val smsRows = sms.queryForThread(threadId, limit)
        val mmsRows = mms.queryForThread(threadId, limit)

        smsRows.forEach { upsertMessage(TelephonyMapper.toEntity(it)) }
        mmsRows.forEach { mirrorMms(it, enrichMetadata = true) }

        reconcileDeletions(threadId, smsRows.map { it.id }, mmsRows.map { it.id }, limit)
        refreshConversationSnippetLocked(threadId)
    }

    /**
     * Removes mirrored messages the provider no longer has.
     *
     * Only safe when the provider query was not truncated by [limit]: otherwise "missing from this
     * page" would be mistaken for "deleted".
     */
    private suspend fun reconcileDeletions(
        threadId: Long,
        smsIds: List<Long>,
        mmsIds: List<Long>,
        limit: Int,
    ) {
        val messageDao = database.messageDao()
        if (smsIds.size < limit) {
            val mirrored = messageDao.idsForThread(threadId, TelephonyMapper.TRANSPORT_SMS)
            if (mirrored.isNotEmpty()) {
                val alive = sms.queryIdsForThread(threadId).toSet()
                val stale = messageDao.getByIds(mirrored)
                    .filter { it.systemId !in alive }
                    .map { it.id }
                if (stale.isNotEmpty()) messageDao.deleteByIds(stale)
            }
        }
        if (mmsIds.size < limit) {
            val mirrored = messageDao.idsForThread(threadId, TelephonyMapper.TRANSPORT_MMS)
            if (mirrored.isNotEmpty()) {
                val alive = mms.queryIdsForThread(threadId).toSet()
                val stale = messageDao.getByIds(mirrored)
                    .filter { it.systemId !in alive }
                    .map { it.id }
                if (stale.isNotEmpty()) messageDao.deleteByIds(stale)
            }
        }
    }

    private suspend fun mirrorMms(
        row: MmsRow,
        enrichMetadata: Boolean,
    ): Long {
        val parts = mms.queryParts(row.id)
        val attachmentParts = TelephonyMapper.attachmentParts(parts)
        val entity = TelephonyMapper.toEntity(
            row = row,
            senderAddress = mms.senderAddress(row.id) ?: fallbackAddress(row.threadId),
            bodyText = TelephonyMapper.bodyTextOf(parts),
            hasAttachments = attachmentParts.isNotEmpty(),
        )
        val localId = upsertMessage(entity)
        if (localId <= 0L) return localId

        val attachmentDao = database.attachmentDao()
        val existing = attachmentDao.forMessage(localId)
        val expected = attachmentParts.map { TelephonyMapper.toAttachmentEntity(localId, it) }
        val unchanged = existing.size == expected.size &&
            existing.map { it.uri }.toSet() == expected.map { it.uri }.toSet()
        if (!unchanged) {
            attachmentDao.deleteForMessage(localId)
            val toInsert = if (enrichMetadata) expected.map(metadataReader::enrich) else expected
            if (toInsert.isNotEmpty()) attachmentDao.insertAll(toInsert)
        } else if (enrichMetadata && existing.any { it.sizeBytes == 0L }) {
            attachmentDao.deleteForMessage(localId)
            attachmentDao.insertAll(existing.map(metadataReader::enrich))
        }
        return localId
    }

    /** Inserts a mirrored message, or updates the provider-owned columns of an existing one. */
    private suspend fun upsertMessage(entity: MessageEntity): Long {
        val dao = database.messageDao()
        val updated = dao.updateMirroredColumns(
            transport = entity.transport,
            systemId = entity.systemId,
            threadId = entity.threadId,
            address = entity.address,
            body = entity.body,
            subject = entity.subject,
            timestamp = entity.timestamp,
            sentTimestamp = entity.sentTimestamp,
            isOutgoing = entity.isOutgoing,
            isRead = entity.isRead,
            status = entity.status,
            errorCode = entity.errorCode,
            subscriptionId = entity.subscriptionId,
            sizeBytes = entity.sizeBytes,
            contentLocation = entity.contentLocation,
            transactionId = entity.transactionId,
            expiryTimestamp = entity.expiryTimestamp,
            hasAttachments = entity.hasAttachments,
        )
        if (updated > 0) {
            return dao.getBySystemId(entity.transport, entity.systemId)?.id ?: 0L
        }
        val inserted = dao.insert(entity)
        return if (inserted > 0) {
            inserted
        } else {
            dao.getBySystemId(entity.transport, entity.systemId)?.id ?: 0L
        }
    }

    private suspend fun ensureConversation(threadId: Long) {
        if (threadId <= 0L) return
        val dao = database.conversationDao()
        if (dao.getConversation(threadId) != null) return

        val canonical = threads.canonicalAddresses()
        val thread = threads.queryThreads().firstOrNull { it.id == threadId }
        val addresses = thread?.let { threads.recipientsFor(it, canonical) }.orEmpty()
        dao.upsert(
            ConversationEntity(
                threadId = threadId,
                addresses = addresses.joinToString(" "),
                snippet = thread?.snippet.orEmpty(),
                snippetIsOutgoing = false,
                snippetHasAttachment = thread?.hasAttachment ?: false,
                lastMessageTimestamp = thread?.dateMillis ?: System.currentTimeMillis(),
                unreadCount = 0,
                messageCount = thread?.messageCount ?: 0,
                systemArchived = thread?.archived ?: false,
                recipientCount = addresses.size.coerceAtLeast(1),
                lastSyncedAt = System.currentTimeMillis(),
            ),
        )
        dao.insertMetadataIfMissing(ConversationMetadataEntity(threadId = threadId))
    }

    /** When an MMS has no From address, the thread's other participant is the best guess. */
    private suspend fun fallbackAddress(threadId: Long): String? =
        database.conversationDao().getConversation(threadId)
            ?.addresses
            ?.let { PhoneNumbers.splitRecipients(it).firstOrNull() }

    private suspend fun refreshUnreadCountsLocked() {
        database.conversationDao().allThreadIds().forEach { refreshUnreadCountLocked(it) }
    }

    private suspend fun refreshUnreadCountLocked(threadId: Long) {
        val count = database.messageDao().unreadCount(threadId)
        database.conversationDao().setUnreadCount(threadId, count)
    }

    private suspend fun refreshSnippetsLocked(threadIds: List<Long>) {
        threadIds.distinct().forEach { refreshConversationSnippetLocked(it) }
    }

    /**
     * Recomputes the snippet from the newest mirrored message.
     *
     * The provider's own snippet is often stale for MMS (it can be blank for a picture with no
     * caption) and never says whether the last message was outgoing, which the list needs for the
     * "You:" prefix and the delivery tick.
     */
    private suspend fun refreshConversationSnippetLocked(threadId: Long) {
        if (threadId <= 0L) return
        val newest = database.messageDao().newestInThread(threadId) ?: return
        val conversationDao = database.conversationDao()
        val existing = conversationDao.getConversation(threadId) ?: return
        conversationDao.upsert(
            ConversationEntity(
                threadId = threadId,
                addresses = existing.addresses,
                snippet = newest.body.orEmpty(),
                snippetIsOutgoing = newest.isOutgoing,
                snippetHasAttachment = newest.hasAttachments,
                lastMessageTimestamp = maxOf(newest.timestamp, existing.lastMessageTimestamp),
                unreadCount = existing.unreadCount,
                messageCount = existing.messageCount,
                systemArchived = existing.archived,
                recipientCount = existing.recipientCount,
                lastSyncedAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        /** Newest messages mirrored on a full pass. Older ones arrive when a thread is opened. */
        const val RECENT_MESSAGE_WINDOW = 2_000

        /** Messages mirrored when a single conversation is opened. */
        const val THREAD_MESSAGE_WINDOW = 500

        /** MMS rows are far rarer and much more expensive to mirror than SMS. */
        private const val MMS_WINDOW_DIVISOR = 4
    }
}
