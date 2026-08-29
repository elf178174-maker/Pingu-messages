package app.pingu.messages.data.repository

import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.entity.ScheduledAttachmentEntity
import app.pingu.messages.data.local.entity.ScheduledMessageEntity
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.ScheduledMessage
import app.pingu.messages.domain.model.ScheduledMessageState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The scheduled-message queue.
 *
 * The repository owns storage only; arming and firing the alarms is [app.pingu.messages.platform
 * .scheduling.ScheduledMessageScheduler], so the queue survives a reboot by being read back rather
 * than by anything staying resident in memory.
 */
class ScheduledMessageRepository(
    private val database: PinguDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val dao get() = database.scheduledMessageDao()

    fun observeAll(): Flow<List<ScheduledMessage>> =
        dao.observeAll().map { list -> list.map { it.toDomain(emptyList()) } }

    fun observeForThread(threadId: Long): Flow<List<ScheduledMessage>> =
        dao.observeForThread(threadId).map { list -> list.map { it.toDomain(emptyList()) } }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun get(id: Long): ScheduledMessage? = withContext(ioDispatcher) {
        val entity = dao.get(id) ?: return@withContext null
        entity.toDomain(dao.attachments(id).map { it.toAttachment() })
    }

    suspend fun pending(): List<ScheduledMessage> = withContext(ioDispatcher) {
        dao.pending().map { it.toDomain(emptyList()) }
    }

    suspend fun due(now: Long = System.currentTimeMillis()): List<ScheduledMessage> =
        withContext(ioDispatcher) { dao.due(now).map { it.toDomain(emptyList()) } }

    suspend fun schedule(message: ScheduledMessage): Long = withContext(ioDispatcher) {
        dao.insertWithAttachments(
            message = ScheduledMessageEntity(
                threadId = message.threadId,
                recipients = message.recipients.joinToString(" "),
                body = message.body,
                subject = message.subject,
                scheduledAt = message.scheduledAt,
                createdAt = System.currentTimeMillis(),
                subscriptionId = message.subscriptionId,
                state = ScheduledMessageState.PENDING.name,
            ),
            attachments = message.attachments.map { it.toScheduledEntity() },
        )
    }

    suspend fun reschedule(id: Long, scheduledAt: Long) = withContext(ioDispatcher) {
        dao.reschedule(id, scheduledAt)
        dao.updateState(id, ScheduledMessageState.PENDING.name, null)
    }

    suspend fun markSending(id: Long) = withContext(ioDispatcher) {
        dao.incrementAttempts(id)
        dao.updateState(id, ScheduledMessageState.SENDING.name, null)
    }

    suspend fun markSent(id: Long) = withContext(ioDispatcher) {
        dao.updateState(id, ScheduledMessageState.SENT.name, null)
    }

    suspend fun markFailed(id: Long, reason: String?) = withContext(ioDispatcher) {
        dao.updateState(id, ScheduledMessageState.FAILED.name, reason)
    }

    suspend fun cancel(id: Long) = withContext(ioDispatcher) {
        dao.updateState(id, ScheduledMessageState.CANCELLED.name, null)
    }

    suspend fun delete(id: Long) = withContext(ioDispatcher) { dao.delete(id) }

    /** Housekeeping: drop sent and cancelled entries older than a week. */
    suspend fun purgeOld(before: Long) = withContext(ioDispatcher) { dao.purgeCompletedBefore(before) }
}

private fun ScheduledMessageEntity.toDomain(attachments: List<Attachment>): ScheduledMessage =
    ScheduledMessage(
        id = id,
        threadId = threadId,
        recipients = PhoneNumbers.splitRecipients(recipients),
        body = body,
        subject = subject,
        attachments = attachments,
        scheduledAt = scheduledAt,
        createdAt = createdAt,
        subscriptionId = subscriptionId,
        state = runCatching { ScheduledMessageState.valueOf(state) }
            .getOrElse { ScheduledMessageState.PENDING },
        failureReason = failureReason,
        attempts = attempts,
    )

private fun ScheduledAttachmentEntity.toAttachment(): Attachment = Attachment(
    id = id,
    uri = uri,
    mimeType = mimeType,
    fileName = fileName,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    extra = extra,
)

private fun Attachment.toScheduledEntity(): ScheduledAttachmentEntity = ScheduledAttachmentEntity(
    scheduledMessageId = 0L,
    uri = uri,
    mimeType = mimeType,
    fileName = fileName,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    extra = extra,
)
