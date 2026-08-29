package app.pingu.messages.data.repository

import android.content.Context
import android.net.Uri
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.entity.AttachmentEntity
import app.pingu.messages.data.local.entity.MessageEntity
import app.pingu.messages.data.local.entity.ReactionEntity
import app.pingu.messages.data.telephony.MmsProviderDataSource
import app.pingu.messages.data.telephony.SmsProviderDataSource
import app.pingu.messages.data.telephony.TelephonyMapper
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.Message
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.domain.model.Reaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Messages within a conversation.
 *
 * Reading is windowed: the screen asks for the newest N and grows N as the user scrolls back, so a
 * thread with tens of thousands of messages costs one indexed query of the visible range rather
 * than a full table read. Attachments and reactions for the loaded window are fetched in two extra
 * queries and joined in memory, which is far cheaper than a correlated query per row.
 */
class MessageRepository(
    private val context: Context,
    private val database: PinguDatabase,
    private val sms: SmsProviderDataSource,
    private val mms: MmsProviderDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val messageDao get() = database.messageDao()
    private val attachmentDao get() = database.attachmentDao()
    private val reactionDao get() = database.reactionDao()

    /** Newest first, matching the reversed layout of the conversation list. */
    fun observeWindow(threadId: Long, limit: Int): Flow<List<Message>> =
        messageDao.observeRecent(threadId, limit).map { entities -> hydrate(entities) }

    suspend fun countInThread(threadId: Long): Int = withContext(ioDispatcher) {
        messageDao.countInThread(threadId)
    }

    /**
     * How many messages have to be loaded for [messageId] to be inside the window, used when
     * jumping to a search result or to a quoted message.
     */
    suspend fun windowSizeToInclude(threadId: Long, messageId: Long, padding: Int = 30): Int =
        withContext(ioDispatcher) {
            val message = messageDao.getById(messageId) ?: return@withContext padding
            val newer = messageDao.countNewerThan(threadId, message.timestamp, message.id)
            newer + padding
        }

    suspend fun getMessage(id: Long): Message? = withContext(ioDispatcher) {
        messageDao.getById(id)?.let { hydrate(listOf(it)).firstOrNull() }
    }

    /** The MMS content location, needed to (re)download a message whose body was not fetched. */
    suspend fun contentLocationOf(messageId: Long): String? = withContext(ioDispatcher) {
        messageDao.getById(messageId)?.contentLocation
    }

    suspend fun getMessages(ids: List<Long>): List<Message> = withContext(ioDispatcher) {
        if (ids.isEmpty()) emptyList() else hydrate(messageDao.getByIds(ids))
    }

    suspend fun attachmentsInThread(threadId: Long, limit: Int = 500): List<Attachment> =
        withContext(ioDispatcher) {
            attachmentDao.forThread(threadId, limit).map { row ->
                Attachment(
                    id = row.id,
                    messageId = row.messageId,
                    uri = row.uri,
                    mimeType = row.mimeType,
                    fileName = row.fileName,
                    sizeBytes = row.sizeBytes,
                    width = row.width,
                    height = row.height,
                    durationMillis = row.durationMillis,
                    extra = row.extra,
                )
            }
        }

    // ---- Mutations ----------------------------------------------------------------------------

    /** Ids of unread incoming messages, used to build the notification's message history. */
    suspend fun unreadIncomingIds(threadId: Long): List<Long> = withContext(ioDispatcher) {
        messageDao.unreadIncoming(threadId).map { it.id }
    }

    suspend fun markRead(threadId: Long) = withContext(ioDispatcher) {
        messageDao.markThreadRead(threadId)
        sms.markThreadRead(threadId)
        mms.markThreadRead(threadId)
        database.conversationDao().setUnreadCount(threadId, 0)
    }

    /**
     * Deletes messages from the system provider and the mirror.
     *
     * There is no such thing as deleting a message from the recipient's phone over SMS, so the app
     * never suggests otherwise: this removes the local copy only, which is exactly what the action
     * says it does.
     */
    suspend fun delete(messageIds: List<Long>) = withContext(ioDispatcher) {
        val entities = messageDao.getByIds(messageIds)
        entities.forEach { entity ->
            if (entity.transport == TelephonyMapper.TRANSPORT_SMS) {
                sms.delete(entity.systemId)
            } else {
                mms.delete(entity.systemId)
            }
        }
        attachmentDao.deleteForMessages(messageIds)
        messageDao.deleteByIds(messageIds)
        entities.map { it.threadId }.distinct().forEach { threadId ->
            database.conversationDao().setUnreadCount(threadId, messageDao.unreadCount(threadId))
        }
    }

    suspend fun setStatus(messageId: Long, status: MessageStatus, errorCode: Int = 0) =
        withContext(ioDispatcher) { messageDao.updateStatus(messageId, status.name, errorCode) }

    suspend fun setReplyLink(messageId: Long, replyToId: Long?, snippet: String?) =
        withContext(ioDispatcher) { messageDao.updateReplyLink(messageId, replyToId, snippet) }

    /** Adds or replaces the local user's reaction on a message. */
    suspend fun setOwnReaction(messageId: Long, emoji: String?, transmitted: Boolean = false) =
        withContext(ioDispatcher) {
            if (emoji == null) {
                reactionDao.remove(messageId, OWN_REACTION_KEY)
            } else {
                reactionDao.upsert(
                    ReactionEntity(
                        messageId = messageId,
                        emoji = emoji,
                        authorKey = OWN_REACTION_KEY,
                        authorAddress = null,
                        timestamp = System.currentTimeMillis(),
                        transmitted = transmitted,
                    ),
                )
            }
        }

    /** Records a reaction that arrived as a text tapback from another person. */
    suspend fun setRemoteReaction(messageId: Long, emoji: String?, authorAddress: String) =
        withContext(ioDispatcher) {
            val key = PhoneNumbers.matchKey(authorAddress).ifEmpty { authorAddress }
            if (emoji == null) {
                reactionDao.remove(messageId, key)
            } else {
                reactionDao.upsert(
                    ReactionEntity(
                        messageId = messageId,
                        emoji = emoji,
                        authorKey = key,
                        authorAddress = authorAddress,
                        timestamp = System.currentTimeMillis(),
                        transmitted = true,
                    ),
                )
            }
        }

    /**
     * Finds the most recent message in a thread whose text matches a quoted tapback, so an
     * incoming `Liked "..."` can be attached to the right bubble.
     */
    suspend fun findMessageByQuotedText(threadId: Long, quoted: String): Message? =
        withContext(ioDispatcher) {
            val needle = quoted.trim().trimEnd('.').removeSuffix("…")
            if (needle.isEmpty()) return@withContext null
            messageDao.recentOnce(threadId, QUOTE_SEARCH_WINDOW)
                .firstOrNull { entity ->
                    val body = entity.body?.trim().orEmpty()
                    body == quoted.trim() || (needle.length >= 8 && body.startsWith(needle))
                }
                ?.let { hydrate(listOf(it)).firstOrNull() }
        }

    suspend fun searchMessages(query: String, limit: Int, offset: Int) = withContext(ioDispatcher) {
        messageDao.searchMessages(query, limit, offset)
    }

    suspend fun countSearchMessages(query: String) = withContext(ioDispatcher) {
        messageDao.countSearchMessages(query)
    }

    suspend fun searchAttachments(query: String, limit: Int) = withContext(ioDispatcher) {
        attachmentDao.search(query, limit)
    }

    /** Opens an attachment for reading; used by the viewer, the exporter and "save to Downloads". */
    fun openAttachment(uri: String) = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))
    }.getOrNull()

    private suspend fun hydrate(entities: List<MessageEntity>): List<Message> {
        if (entities.isEmpty()) return emptyList()
        val ids = entities.map { it.id }
        val attachments = attachmentDao.forMessages(ids).groupBy { it.messageId }
        val reactions = reactionDao.forMessages(ids).groupBy { it.messageId }
        return entities.map { entity ->
            entity.toMessage(
                attachments = attachments[entity.id].orEmpty(),
                reactions = reactions[entity.id].orEmpty(),
            )
        }
    }

    private companion object {
        /** Author key used for the local user's own reactions. */
        const val OWN_REACTION_KEY = ""

        /** How far back to look when matching an incoming tapback to its message. */
        const val QUOTE_SEARCH_WINDOW = 100
    }
}

internal fun MessageEntity.toMessage(
    attachments: List<AttachmentEntity>,
    reactions: List<ReactionEntity>,
): Message = Message(
    id = id,
    threadId = threadId,
    systemId = systemId,
    transport = TelephonyMapper.transportFrom(transport),
    address = address,
    body = body,
    subject = subject,
    timestamp = timestamp,
    sentTimestamp = sentTimestamp,
    isOutgoing = isOutgoing,
    isRead = isRead,
    status = runCatching { MessageStatus.valueOf(status) }
        .getOrElse { if (isOutgoing) MessageStatus.SENT else MessageStatus.RECEIVED },
    errorCode = errorCode,
    subscriptionId = subscriptionId,
    attachments = attachments.map { it.toAttachment() },
    reactions = reactions.map { it.toReaction() },
    replyToMessageId = replyToMessageId,
    replyToSnippet = replyToSnippet,
    sizeBytes = sizeBytes,
)

internal fun AttachmentEntity.toAttachment(): Attachment = Attachment(
    id = id,
    messageId = messageId,
    uri = uri,
    mimeType = mimeType,
    fileName = fileName,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    extra = extra,
)

internal fun ReactionEntity.toReaction(): Reaction = Reaction(
    id = id,
    messageId = messageId,
    emoji = emoji,
    authorAddress = authorAddress,
    timestamp = timestamp,
    transmitted = transmitted,
)
