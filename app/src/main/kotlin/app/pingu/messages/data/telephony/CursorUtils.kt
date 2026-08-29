package app.pingu.messages.data.telephony

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * Cursor helpers.
 *
 * Column indexes are resolved by name and may be absent: OEM variants of the telephony provider do
 * drop columns (`archived` and `sub_id` are the usual casualties), and an app that assumes they are
 * there crashes on exactly the devices it cannot test on. Every accessor therefore tolerates a
 * missing column and returns a default.
 */
internal object CursorUtils {

    private const val TAG = "CursorUtils"

    fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    fun Cursor.stringOr(column: String, fallback: String): String =
        stringOrNull(column) ?: fallback

    fun Cursor.longOr(column: String, fallback: Long = 0L): Long {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else fallback
    }

    fun Cursor.intOr(column: String, fallback: Int = 0): Int {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index) else fallback
    }

    fun Cursor.booleanOr(column: String, fallback: Boolean = false): Boolean {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index) != 0 else fallback
    }

    /**
     * Runs a query and maps every row, closing the cursor and swallowing the provider failures that
     * are expected in normal operation: a revoked permission, or an OEM provider rejecting a
     * projection. Returns an empty list in those cases so a sync can continue with the rest.
     */
    inline fun <T> ContentResolver.queryAll(
        uri: Uri,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        crossinline mapper: (Cursor) -> T?,
    ): List<T> {
        val results = ArrayList<T>()
        try {
            query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    mapper(cursor)?.let(results::add)
                }
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "No permission to read $uri", error)
        } catch (error: Exception) {
            Log.w(TAG, "Query failed for $uri", error)
        }
        return results
    }

    /** Runs a query expecting at most one row. */
    inline fun <T> ContentResolver.queryFirst(
        uri: Uri,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        crossinline mapper: (Cursor) -> T?,
    ): T? = queryAll(uri, projection, selection, selectionArgs, sortOrder, mapper).firstOrNull()
}
