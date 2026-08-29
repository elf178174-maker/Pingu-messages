package app.pingu.messages.data.telephony

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import app.pingu.messages.data.mms.pdu.MmsPart
import app.pingu.messages.data.mms.pdu.PduCharsets
import app.pingu.messages.data.mms.pdu.PduHeaders
import app.pingu.messages.data.telephony.CursorUtils.booleanOr
import app.pingu.messages.data.telephony.CursorUtils.intOr
import app.pingu.messages.data.telephony.CursorUtils.longOr
import app.pingu.messages.data.telephony.CursorUtils.queryAll
import app.pingu.messages.data.telephony.CursorUtils.queryFirst
import app.pingu.messages.data.telephony.CursorUtils.stringOrNull

/** One row of `content://mms`. Dates are seconds here, unlike the SMS table. */
data class MmsRow(
    val id: Long,
    val threadId: Long,
    val dateSeconds: Long,
    val dateSentSeconds: Long,
    val messageBox: Int,
    val messageType: Int,
    val read: Boolean,
    val seen: Boolean,
    val subject: String?,
    val subjectCharset: Int,
    val messageSize: Long,
    val contentLocation: String?,
    val transactionId: String?,
    val messageId: String?,
    val expirySeconds: Long,
    val subscriptionId: Int,
    val responseStatus: Int,
    val retrieveStatus: Int,
    val status: Int,
    val locked: Boolean,
) {
    val dateMillis: Long get() = dateSeconds * 1000L
    val dateSentMillis: Long get() = dateSentSeconds * 1000L

    val isOutgoing: Boolean
        get() = messageBox == Telephony.Mms.MESSAGE_BOX_SENT ||
            messageBox == Telephony.Mms.MESSAGE_BOX_OUTBOX ||
            messageBox == Telephony.Mms.MESSAGE_BOX_FAILED ||
            messageBox == Telephony.Mms.MESSAGE_BOX_DRAFTS
}

/** One row of `content://mms/part`. */
data class MmsPartRow(
    val id: Long,
    val messageId: Long,
    val sequence: Int,
    val contentType: String,
    val name: String?,
    val fileName: String?,
    val contentId: String?,
    val contentLocation: String?,
    val charset: Int,
    val text: String?,
    val dataPath: String?,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MmsProviderDataSource.PART_CONTENT_URI, id)

    val isText: Boolean get() = contentType.startsWith("text/", ignoreCase = true)

    val isSmil: Boolean get() = contentType.equals("application/smil", ignoreCase = true)
}

/**
 * Reads and writes the system MMS store, including parts and addresses.
 *
 * MMS is spread across three tables that must be kept consistent: the message row, its parts, and
 * its addresses. Every write here does all three, which is what other messaging apps (and the
 * platform's own MMS handling) expect to find.
 */
class MmsProviderDataSource(private val context: Context) {

    private val resolver get() = context.contentResolver

    companion object {
        private const val TAG = "MmsProvider"

        val PART_CONTENT_URI: Uri = Uri.parse("content://mms/part")

        private val PROJECTION = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.DATE_SENT,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.MESSAGE_TYPE,
            Telephony.Mms.READ,
            Telephony.Mms.SEEN,
            Telephony.Mms.SUBJECT,
            Telephony.Mms.SUBJECT_CHARSET,
            Telephony.Mms.MESSAGE_SIZE,
            Telephony.Mms.CONTENT_LOCATION,
            Telephony.Mms.TRANSACTION_ID,
            Telephony.Mms.MESSAGE_ID,
            Telephony.Mms.EXPIRY,
            Telephony.Mms.SUBSCRIPTION_ID,
            Telephony.Mms.RESPONSE_STATUS,
            Telephony.Mms.RETRIEVE_STATUS,
            Telephony.Mms.STATUS,
            Telephony.Mms.LOCKED,
        )

        private val PART_PROJECTION = arrayOf(
            Telephony.Mms.Part._ID,
            Telephony.Mms.Part.MSG_ID,
            Telephony.Mms.Part.SEQ,
            Telephony.Mms.Part.CONTENT_TYPE,
            Telephony.Mms.Part.NAME,
            Telephony.Mms.Part.FILENAME,
            Telephony.Mms.Part.CONTENT_ID,
            Telephony.Mms.Part.CONTENT_LOCATION,
            Telephony.Mms.Part.CHARSET,
            Telephony.Mms.Part.TEXT,
            Telephony.Mms.Part._DATA,
        )

        private const val ORDER_NEWEST_FIRST = "${Telephony.Mms.DATE} DESC, ${Telephony.Mms._ID} DESC"
    }

    private fun map(cursor: android.database.Cursor): MmsRow = MmsRow(
        id = cursor.longOr(Telephony.Mms._ID),
        threadId = cursor.longOr(Telephony.Mms.THREAD_ID),
        dateSeconds = cursor.longOr(Telephony.Mms.DATE),
        dateSentSeconds = cursor.longOr(Telephony.Mms.DATE_SENT),
        messageBox = cursor.intOr(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX),
        messageType = cursor.intOr(Telephony.Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF),
        read = cursor.booleanOr(Telephony.Mms.READ, true),
        seen = cursor.booleanOr(Telephony.Mms.SEEN, true),
        subject = cursor.stringOrNull(Telephony.Mms.SUBJECT),
        subjectCharset = cursor.intOr(Telephony.Mms.SUBJECT_CHARSET, PduCharsets.UTF_8),
        messageSize = cursor.longOr(Telephony.Mms.MESSAGE_SIZE),
        contentLocation = cursor.stringOrNull(Telephony.Mms.CONTENT_LOCATION),
        transactionId = cursor.stringOrNull(Telephony.Mms.TRANSACTION_ID),
        messageId = cursor.stringOrNull(Telephony.Mms.MESSAGE_ID),
        expirySeconds = cursor.longOr(Telephony.Mms.EXPIRY),
        subscriptionId = cursor.intOr(Telephony.Mms.SUBSCRIPTION_ID, -1),
        responseStatus = cursor.intOr(Telephony.Mms.RESPONSE_STATUS),
        retrieveStatus = cursor.intOr(Telephony.Mms.RETRIEVE_STATUS),
        status = cursor.intOr(Telephony.Mms.STATUS),
        locked = cursor.booleanOr(Telephony.Mms.LOCKED),
    )

    private fun mapPart(cursor: android.database.Cursor): MmsPartRow = MmsPartRow(
        id = cursor.longOr(Telephony.Mms.Part._ID),
        messageId = cursor.longOr(Telephony.Mms.Part.MSG_ID),
        sequence = cursor.intOr(Telephony.Mms.Part.SEQ),
        contentType = cursor.stringOrNull(Telephony.Mms.Part.CONTENT_TYPE)
            ?: "application/octet-stream",
        name = cursor.stringOrNull(Telephony.Mms.Part.NAME),
        fileName = cursor.stringOrNull(Telephony.Mms.Part.FILENAME),
        contentId = cursor.stringOrNull(Telephony.Mms.Part.CONTENT_ID),
        contentLocation = cursor.stringOrNull(Telephony.Mms.Part.CONTENT_LOCATION),
        charset = cursor.intOr(Telephony.Mms.Part.CHARSET, PduCharsets.UTF_8),
        text = cursor.stringOrNull(Telephony.Mms.Part.TEXT),
        dataPath = cursor.stringOrNull(Telephony.Mms.Part._DATA),
    )

    fun queryRecent(limit: Int): List<MmsRow> = resolver.queryAll(
        uri = Telephony.Mms.CONTENT_URI,
        projection = PROJECTION,
        sortOrder = "$ORDER_NEWEST_FIRST LIMIT $limit",
        mapper = ::map,
    )

    fun queryForThread(threadId: Long, limit: Int): List<MmsRow> = resolver.queryAll(
        uri = Telephony.Mms.CONTENT_URI,
        projection = PROJECTION,
        selection = "${Telephony.Mms.THREAD_ID} = ?",
        selectionArgs = arrayOf(threadId.toString()),
        sortOrder = "$ORDER_NEWEST_FIRST LIMIT $limit",
        mapper = ::map,
    )

    fun queryById(id: Long): MmsRow? = resolver.queryFirst(
        uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id),
        projection = PROJECTION,
        mapper = ::map,
    )

    fun queryByUri(uri: Uri): MmsRow? =
        resolver.queryFirst(uri = uri, projection = PROJECTION, mapper = ::map)

    fun queryIdsForThread(threadId: Long): List<Long> = resolver.queryAll(
        uri = Telephony.Mms.CONTENT_URI,
        projection = arrayOf(Telephony.Mms._ID),
        selection = "${Telephony.Mms.THREAD_ID} = ?",
        selectionArgs = arrayOf(threadId.toString()),
        mapper = { it.longOr(Telephony.Mms._ID) },
    )

    fun queryAllIds(): List<Long> = resolver.queryAll(
        uri = Telephony.Mms.CONTENT_URI,
        projection = arrayOf(Telephony.Mms._ID),
        mapper = { it.longOr(Telephony.Mms._ID) },
    )

    fun queryByTransactionId(transactionId: String): MmsRow? = resolver.queryFirst(
        uri = Telephony.Mms.CONTENT_URI,
        projection = PROJECTION,
        selection = "${Telephony.Mms.TRANSACTION_ID} = ?",
        selectionArgs = arrayOf(transactionId),
        mapper = ::map,
    )

    fun queryByMessageId(messageId: String): MmsRow? = resolver.queryFirst(
        uri = Telephony.Mms.CONTENT_URI,
        projection = PROJECTION,
        selection = "${Telephony.Mms.MESSAGE_ID} = ?",
        selectionArgs = arrayOf(messageId),
        mapper = ::map,
    )

    fun queryParts(messageId: Long): List<MmsPartRow> = resolver.queryAll(
        uri = PART_CONTENT_URI,
        projection = PART_PROJECTION,
        selection = "${Telephony.Mms.Part.MSG_ID} = ?",
        selectionArgs = arrayOf(messageId.toString()),
        sortOrder = "${Telephony.Mms.Part.SEQ} ASC, ${Telephony.Mms.Part._ID} ASC",
        mapper = ::mapPart,
    )

    /** Addresses of an MMS, keyed by the PDU address type (From, To, Cc, Bcc). */
    fun queryAddresses(messageId: Long): List<Pair<Int, String>> {
        val uri = Uri.parse("content://mms/$messageId/addr")
        return resolver.queryAll(
            uri = uri,
            projection = arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            mapper = { cursor ->
                val address = cursor.stringOrNull(Telephony.Mms.Addr.ADDRESS) ?: return@queryAll null
                cursor.intOr(Telephony.Mms.Addr.TYPE, PduHeaders.TO) to address
            },
        )
    }

    fun senderAddress(messageId: Long): String? =
        queryAddresses(messageId).firstOrNull { it.first == PduHeaders.FROM }?.second

    fun recipientAddresses(messageId: Long): List<String> =
        queryAddresses(messageId).filter { it.first != PduHeaders.FROM }.map { it.second }

    fun openPart(partId: Long) =
        resolver.openInputStream(ContentUris.withAppendedId(PART_CONTENT_URI, partId))

    // ---- Writing ----------------------------------------------------------------------------

    /**
     * Stores a complete MMS: the message row, its parts and its addresses.
     *
     * @return the message URI, or null when the provider refused the write (which happens when the
     * app is not the default SMS app).
     */
    fun insertMessage(
        threadId: Long,
        messageBox: Int,
        messageType: Int,
        dateSeconds: Long,
        dateSentSeconds: Long,
        subject: String?,
        subjectCharset: Int,
        read: Boolean,
        subscriptionId: Int,
        transactionId: String?,
        messageId: String?,
        contentLocation: String?,
        expirySeconds: Long,
        messageSize: Long,
        from: String?,
        to: List<String>,
        parts: List<MmsPart>,
    ): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, dateSeconds)
            put(Telephony.Mms.DATE_SENT, dateSentSeconds)
            put(Telephony.Mms.MESSAGE_BOX, messageBox)
            put(Telephony.Mms.MESSAGE_TYPE, messageType)
            put(Telephony.Mms.READ, if (read) 1 else 0)
            put(Telephony.Mms.SEEN, if (read) 1 else 0)
            put(Telephony.Mms.MMS_VERSION, PduHeaders.MMS_VERSION_1_2)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            if (!subject.isNullOrEmpty()) {
                put(Telephony.Mms.SUBJECT, subject)
                put(Telephony.Mms.SUBJECT_CHARSET, subjectCharset)
            }
            if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
            transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            contentLocation?.let { put(Telephony.Mms.CONTENT_LOCATION, it) }
            if (expirySeconds > 0) put(Telephony.Mms.EXPIRY, expirySeconds)
            if (messageSize > 0) put(Telephony.Mms.MESSAGE_SIZE, messageSize)
        }

        val uri = try {
            resolver.insert(Telephony.Mms.CONTENT_URI, values)
        } catch (error: Exception) {
            Log.w(TAG, "MMS insert failed", error)
            null
        } ?: return null

        val id = ContentUris.parseId(uri)
        parts.forEachIndexed { index, part -> insertPart(id, index, part) }
        from?.let { insertAddress(id, it, PduHeaders.FROM) }
        to.forEach { insertAddress(id, it, PduHeaders.TO) }
        return uri
    }

    private fun insertPart(messageRowId: Long, sequence: Int, part: MmsPart) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.MSG_ID, messageRowId)
            put(Telephony.Mms.Part.SEQ, if (part.isSmil) -1 else sequence)
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            part.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, "<$it>") }
            part.contentLocation?.let { put(Telephony.Mms.Part.CONTENT_LOCATION, it) }
            part.name?.let { put(Telephony.Mms.Part.NAME, it) }
            part.fileName?.let { put(Telephony.Mms.Part.FILENAME, it) }
            if (part.isText) {
                put(Telephony.Mms.Part.CHARSET, part.charsetMib)
                put(Telephony.Mms.Part.TEXT, part.text)
            }
        }
        try {
            val partUri = resolver.insert(Uri.parse("content://mms/$messageRowId/part"), values)
                ?: return
            if (!part.isText && part.data.isNotEmpty()) {
                resolver.openOutputStream(partUri)?.use { it.write(part.data) }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Part insert failed for message $messageRowId", error)
        }
    }

    private fun insertAddress(messageRowId: Long, address: String, type: Int) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.TYPE, type)
            put(Telephony.Mms.Addr.CHARSET, PduCharsets.UTF_8)
            put(Telephony.Mms.Addr.MSG_ID, messageRowId)
        }
        try {
            resolver.insert(Uri.parse("content://mms/$messageRowId/addr"), values)
        } catch (error: Exception) {
            Log.w(TAG, "Address insert failed for message $messageRowId", error)
        }
    }

    fun updateMessageBox(id: Long, messageBox: Int): Int = update(
        id,
        ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, messageBox) },
    )

    fun updateRetrieveStatus(id: Long, retrieveStatus: Int): Int = update(
        id,
        ContentValues().apply { put(Telephony.Mms.RETRIEVE_STATUS, retrieveStatus) },
    )

    fun updateStatus(id: Long, status: Int): Int = update(
        id,
        ContentValues().apply { put(Telephony.Mms.STATUS, status) },
    )

    fun markThreadRead(threadId: Long): Int = try {
        resolver.update(
            Telephony.Mms.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
            },
            "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
            arrayOf(threadId.toString()),
        )
    } catch (error: Exception) {
        Log.w(TAG, "Mark read failed for thread $threadId", error)
        0
    }

    fun markUnread(id: Long): Int = update(
        id,
        ContentValues().apply { put(Telephony.Mms.READ, 0) },
    )

    fun delete(id: Long): Boolean = try {
        resolver.delete(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id), null, null) > 0
    } catch (error: Exception) {
        Log.w(TAG, "MMS delete failed for $id", error)
        false
    }

    fun deleteThread(threadId: Long): Int = try {
        resolver.delete(
            Telephony.Mms.CONTENT_URI,
            "${Telephony.Mms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
        )
    } catch (error: Exception) {
        Log.w(TAG, "MMS thread delete failed for $threadId", error)
        0
    }

    private fun update(id: Long, values: ContentValues): Int = try {
        resolver.update(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id), values, null, null)
    } catch (error: Exception) {
        Log.w(TAG, "MMS update failed for $id", error)
        0
    }
}
