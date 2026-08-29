package app.pingu.messages.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.util.Log
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.entity.DraftAttachmentEntity
import app.pingu.messages.data.local.entity.DraftEntity
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.Draft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Composer drafts.
 *
 * A draft is written locally on every pause of typing and, for threads that already exist, mirrored
 * into the system SMS draft box. That mirror is what makes the draft visible if the user switches
 * messaging apps, and it is why the app deletes the provider draft as soon as the message is sent:
 * a stale draft row shows up as a phantom unsent message everywhere else on the phone.
 */
class DraftRepository(
    private val context: Context,
    private val database: PinguDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val dao get() = database.draftDao()

    fun observe(threadId: Long): Flow<Draft?> = dao.observe(threadId).map { rows ->
        rows.firstOrNull()?.let { Draft(threadId = it.threadId, text = it.text, subject = it.subject) }
    }

    suspend fun get(threadId: Long): Draft? = withContext(ioDispatcher) {
        val entity = dao.get(threadId) ?: return@withContext null
        Draft(
            threadId = entity.threadId,
            text = entity.text,
            subject = entity.subject,
            attachments = dao.attachments(threadId).map { it.toAttachment() },
            replyToMessageId = entity.replyToMessageId,
            replyToSnippet = entity.replyToSnippet,
            subscriptionId = entity.subscriptionId,
            updatedAt = entity.updatedAt,
        )
    }

    suspend fun save(draft: Draft) = withContext(ioDispatcher) {
        if (draft.isEmpty) {
            clear(draft.threadId)
            return@withContext
        }
        dao.replace(
            draft = DraftEntity(
                threadId = draft.threadId,
                text = draft.text,
                subject = draft.subject,
                replyToMessageId = draft.replyToMessageId,
                replyToSnippet = draft.replyToSnippet,
                subscriptionId = draft.subscriptionId,
                updatedAt = System.currentTimeMillis(),
            ),
            attachments = draft.attachments.map { it.toDraftEntity(draft.threadId) },
        )
        if (draft.threadId > 0) mirrorToProvider(draft)
    }

    suspend fun clear(threadId: Long) = withContext(ioDispatcher) {
        dao.clear(threadId)
        if (threadId > 0) deleteProviderDraft(threadId)
    }

    /** Writes the draft text into `content://sms/draft` so other messaging apps can see it. */
    private fun mirrorToProvider(draft: Draft) {
        if (draft.text.isBlank()) {
            deleteProviderDraft(draft.threadId)
            return
        }
        try {
            deleteProviderDraft(draft.threadId)
            context.contentResolver.insert(
                Telephony.Sms.Draft.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.THREAD_ID, draft.threadId)
                    put(Telephony.Sms.BODY, draft.text)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                },
            )
        } catch (error: Exception) {
            // Not being the default SMS app makes this fail; the local draft is still saved.
            Log.d(TAG, "Could not mirror the draft into the provider", error)
        }
    }

    private fun deleteProviderDraft(threadId: Long) {
        try {
            context.contentResolver.delete(
                Telephony.Sms.Draft.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
            )
        } catch (error: Exception) {
            Log.d(TAG, "Could not clear the provider draft", error)
        }
    }

    private companion object {
        const val TAG = "DraftRepository"
    }
}

private fun DraftAttachmentEntity.toAttachment(): Attachment = Attachment(
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

private fun Attachment.toDraftEntity(threadId: Long): DraftAttachmentEntity = DraftAttachmentEntity(
    threadId = threadId,
    uri = uri,
    mimeType = mimeType,
    fileName = fileName,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    extra = extra,
)
