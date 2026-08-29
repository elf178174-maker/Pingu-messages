package app.pingu.messages.data.telephony

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import app.pingu.messages.data.telephony.CursorUtils.booleanOr
import app.pingu.messages.data.telephony.CursorUtils.intOr
import app.pingu.messages.data.telephony.CursorUtils.longOr
import app.pingu.messages.data.telephony.CursorUtils.queryAll
import app.pingu.messages.data.telephony.CursorUtils.queryFirst
import app.pingu.messages.data.telephony.CursorUtils.stringOrNull

/** One row of `content://sms`. */
data class SmsRow(
    val id: Long,
    val threadId: Long,
    val address: String?,
    val body: String?,
    val subject: String?,
    val dateMillis: Long,
    val dateSentMillis: Long,
    val read: Boolean,
    val seen: Boolean,
    val type: Int,
    val status: Int,
    val errorCode: Int,
    val subscriptionId: Int,
    val locked: Boolean,
) {
    val isOutgoing: Boolean
        get() = type != Telephony.Sms.MESSAGE_TYPE_INBOX && type != Telephony.Sms.MESSAGE_TYPE_ALL
}

/**
 * Reads and writes the system SMS store.
 *
 * As the default SMS app, Pingu Messages is the component responsible for putting received
 * messages into `content://sms`; the platform deliberately does not do it for us. Keeping the
 * system store authoritative is also what makes messages survive a switch to another SMS app.
 */
class SmsProviderDataSource(private val context: Context) {

    private val resolver get() = context.contentResolver

    companion object {
        private const val TAG = "SmsProvider"

        /** Columns requested explicitly; `SELECT *` on this provider is noticeably slower. */
        private val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.SUBJECT,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS,
            Telephony.Sms.ERROR_CODE,
            Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.LOCKED,
        )

        private const val ORDER_NEWEST_FIRST = "${Telephony.Sms.DATE} DESC, ${Telephony.Sms._ID} DESC"
    }

    private fun map(cursor: android.database.Cursor): SmsRow = SmsRow(
        id = cursor.longOr(Telephony.Sms._ID),
        threadId = cursor.longOr(Telephony.Sms.THREAD_ID),
        address = cursor.stringOrNull(Telephony.Sms.ADDRESS),
        body = cursor.stringOrNull(Telephony.Sms.BODY),
        subject = cursor.stringOrNull(Telephony.Sms.SUBJECT),
        dateMillis = cursor.longOr(Telephony.Sms.DATE),
        dateSentMillis = cursor.longOr(Telephony.Sms.DATE_SENT),
        read = cursor.booleanOr(Telephony.Sms.READ, true),
        seen = cursor.booleanOr(Telephony.Sms.SEEN, true),
        type = cursor.intOr(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX),
        status = cursor.intOr(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE),
        errorCode = cursor.intOr(Telephony.Sms.ERROR_CODE),
        subscriptionId = cursor.intOr(Telephony.Sms.SUBSCRIPTION_ID, -1),
        locked = cursor.booleanOr(Telephony.Sms.LOCKED),
    )

    fun queryRecent(limit: Int): List<SmsRow> = resolver.queryAll(
        uri = Telephony.Sms.CONTENT_URI,
        projection = PROJECTION,
        sortOrder = "$ORDER_NEWEST_FIRST LIMIT $limit",
        mapper = ::map,
    )

    fun queryForThread(threadId: Long, limit: Int): List<SmsRow> = resolver.queryAll(
        uri = Telephony.Sms.CONTENT_URI,
        projection = PROJECTION,
        selection = "${Telephony.Sms.THREAD_ID} = ?",
        selectionArgs = arrayOf(threadId.toString()),
        sortOrder = "$ORDER_NEWEST_FIRST LIMIT $limit",
        mapper = ::map,
    )

    fun queryById(id: Long): SmsRow? = resolver.queryFirst(
        uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
        projection = PROJECTION,
        mapper = ::map,
    )

    fun queryByUri(uri: Uri): SmsRow? =
        resolver.queryFirst(uri = uri, projection = PROJECTION, mapper = ::map)

    fun queryIdsForThread(threadId: Long): List<Long> = resolver.queryAll(
        uri = Telephony.Sms.CONTENT_URI,
        projection = arrayOf(Telephony.Sms._ID),
        selection = "${Telephony.Sms.THREAD_ID} = ?",
        selectionArgs = arrayOf(threadId.toString()),
        mapper = { it.longOr(Telephony.Sms._ID) },
    )

    fun queryAllIds(): List<Long> = resolver.queryAll(
        uri = Telephony.Sms.CONTENT_URI,
        projection = arrayOf(Telephony.Sms._ID),
        mapper = { it.longOr(Telephony.Sms._ID) },
    )

    /** Writes a received message into the inbox. Only the default SMS app may do this. */
    fun insertReceived(
        address: String,
        body: String,
        timestampMillis: Long,
        sentTimestampMillis: Long,
        subscriptionId: Int,
        read: Boolean,
        threadId: Long?,
    ): Uri? = insert(
        Telephony.Sms.CONTENT_URI,
        ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestampMillis)
            put(Telephony.Sms.DATE_SENT, sentTimestampMillis)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            threadId?.let { put(Telephony.Sms.THREAD_ID, it) }
        },
    )

    /** Writes an outgoing message that is being handed to the radio. */
    fun insertOutgoing(
        address: String,
        body: String,
        timestampMillis: Long,
        subscriptionId: Int,
        threadId: Long?,
    ): Uri? = insert(
        Telephony.Sms.CONTENT_URI,
        ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestampMillis)
            put(Telephony.Sms.DATE_SENT, timestampMillis)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            threadId?.let { put(Telephony.Sms.THREAD_ID, it) }
        },
    )

    fun markSent(uri: Uri) = update(
        uri,
        ContentValues().apply {
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.ERROR_CODE, 0)
        },
    )

    fun markFailed(uri: Uri, errorCode: Int) = update(
        uri,
        ContentValues().apply {
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
            put(Telephony.Sms.ERROR_CODE, errorCode)
        },
    )

    fun updateDeliveryStatus(uri: Uri, status: Int) = update(
        uri,
        ContentValues().apply { put(Telephony.Sms.STATUS, status) },
    )

    fun markThreadRead(threadId: Long): Int = update(
        Telephony.Sms.CONTENT_URI,
        ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        },
        selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
        selectionArgs = arrayOf(threadId.toString()),
    )

    fun markUnread(id: Long): Int = update(
        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
        ContentValues().apply { put(Telephony.Sms.READ, 0) },
    )

    fun delete(id: Long): Boolean = delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id))

    fun delete(uri: Uri): Boolean = try {
        resolver.delete(uri, null, null) > 0
    } catch (error: Exception) {
        Log.w(TAG, "Delete failed for $uri", error)
        false
    }

    fun deleteThread(threadId: Long): Int = try {
        resolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
        )
    } catch (error: Exception) {
        Log.w(TAG, "Thread delete failed for $threadId", error)
        0
    }

    private fun insert(uri: Uri, values: ContentValues): Uri? = try {
        resolver.insert(uri, values)
    } catch (error: Exception) {
        Log.w(TAG, "Insert failed into $uri", error)
        null
    }

    private fun update(
        uri: Uri,
        values: ContentValues,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
    ): Int = try {
        resolver.update(uri, values, selection, selectionArgs)
    } catch (error: Exception) {
        Log.w(TAG, "Update failed for $uri", error)
        0
    }
}
