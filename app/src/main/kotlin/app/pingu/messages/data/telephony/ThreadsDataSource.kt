package app.pingu.messages.data.telephony

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.telephony.CursorUtils.booleanOr
import app.pingu.messages.data.telephony.CursorUtils.intOr
import app.pingu.messages.data.telephony.CursorUtils.longOr
import app.pingu.messages.data.telephony.CursorUtils.queryAll
import app.pingu.messages.data.telephony.CursorUtils.stringOrNull

/** A row of the system thread table, as returned by the "simple" conversations query. */
data class ThreadRow(
    val id: Long,
    val dateMillis: Long,
    val messageCount: Int,
    val recipientIds: List<Long>,
    val snippet: String,
    val read: Boolean,
    val archived: Boolean,
    val hasAttachment: Boolean,
)

/**
 * The system thread table.
 *
 * Threads are the interoperable identity of a conversation: `Telephony.Threads.getOrCreateThreadId`
 * returns the same id every messaging app on the device uses for the same set of recipients, which
 * is why the app builds on it rather than inventing its own conversation ids.
 *
 * Recipients are stored as ids into a separate table of canonical addresses, so resolving a thread
 * to phone numbers takes two queries; the canonical address table is small and is cached per sync.
 */
class ThreadsDataSource(private val context: Context) {

    private val resolver get() = context.contentResolver

    companion object {
        private const val TAG = "ThreadsDataSource"

        private val CANONICAL_ADDRESSES: Uri = Uri.parse("content://mms-sms/canonical-addresses")

        /**
         * `Telephony.Threads.CONTENT_URI` carries a `?simple=true` query parameter, so an id
         * cannot simply be appended to it. Per-thread operations use the plain conversations URI.
         */
        private val CONVERSATIONS_URI: Uri = Uri.parse("content://mms-sms/conversations")

        private const val COLUMN_RECIPIENT_IDS = "recipient_ids"
        private const val COLUMN_SNIPPET = "snippet"
        private const val COLUMN_MESSAGE_COUNT = "message_count"
        private const val COLUMN_HAS_ATTACHMENT = "has_attachment"
        private const val COLUMN_ARCHIVED = "archived"
        private const val COLUMN_ADDRESS = "address"
    }

    /**
     * All threads, newest first.
     *
     * The projection is intentionally null: OEM providers differ on which of `archived` and
     * `has_attachment` exist, and asking for a column that is missing throws, while reading it
     * defensively from a full row does not.
     */
    fun queryThreads(limit: Int = 0): List<ThreadRow> {
        val sortOrder = buildString {
            append("${Telephony.Threads.DATE} DESC")
            if (limit > 0) append(" LIMIT $limit")
        }
        return resolver.queryAll(
            uri = Telephony.Threads.CONTENT_URI,
            sortOrder = sortOrder,
            mapper = { cursor ->
                ThreadRow(
                    id = cursor.longOr(Telephony.Threads._ID),
                    dateMillis = cursor.longOr(Telephony.Threads.DATE),
                    messageCount = cursor.intOr(COLUMN_MESSAGE_COUNT),
                    recipientIds = cursor.stringOrNull(COLUMN_RECIPIENT_IDS)
                        .orEmpty()
                        .split(' ')
                        .mapNotNull { it.trim().toLongOrNull() },
                    snippet = cursor.stringOrNull(COLUMN_SNIPPET).orEmpty(),
                    read = cursor.booleanOr(Telephony.Threads.READ, true),
                    archived = cursor.booleanOr(COLUMN_ARCHIVED, false),
                    hasAttachment = cursor.booleanOr(COLUMN_HAS_ATTACHMENT, false),
                )
            },
        )
    }

    /** Maps canonical address ids to the addresses themselves. */
    fun canonicalAddresses(): Map<Long, String> {
        val result = HashMap<Long, String>()
        resolver.queryAll(
            uri = CANONICAL_ADDRESSES,
            mapper = { cursor ->
                val id = cursor.longOr("_id")
                val address = cursor.stringOrNull(COLUMN_ADDRESS)
                if (address != null) result[id] = address
                null
            },
        )
        return result
    }

    /**
     * The thread id for a set of recipients, creating it when it does not exist yet.
     *
     * Returns null when the platform refuses, which happens on devices without telephony.
     */
    fun getOrCreateThreadId(recipients: Collection<String>): Long? {
        val cleaned = recipients.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (cleaned.isEmpty()) return null
        return try {
            Telephony.Threads.getOrCreateThreadId(context, cleaned)
        } catch (error: Exception) {
            Log.w(TAG, "Could not resolve a thread id for $cleaned", error)
            null
        }
    }

    /** Marks a thread archived in the system provider so other messaging apps agree. */
    fun setArchived(threadId: Long, archived: Boolean): Boolean = try {
        val uri = ContentUris.withAppendedId(CONVERSATIONS_URI, threadId)
        resolver.update(
            uri,
            ContentValues().apply { put(COLUMN_ARCHIVED, if (archived) 1 else 0) },
            null,
            null,
        ) > 0
    } catch (error: Exception) {
        // Not every provider exposes the archived column for third-party writes; the app keeps its
        // own flag either way, so this is a best-effort nicety rather than a requirement.
        Log.d(TAG, "Provider does not accept archive updates", error)
        false
    }

    fun deleteThread(threadId: Long): Boolean = try {
        val uri = ContentUris.withAppendedId(CONVERSATIONS_URI, threadId)
        resolver.delete(uri, null, null) > 0
    } catch (error: Exception) {
        Log.w(TAG, "Thread delete failed for $threadId", error)
        false
    }

    /** Resolves the recipients of a thread, preferring the canonical address table. */
    fun recipientsFor(thread: ThreadRow, canonical: Map<Long, String>): List<String> =
        thread.recipientIds.mapNotNull { canonical[it] }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { PhoneNumbers.matchKey(it).ifEmpty { it } }
}
