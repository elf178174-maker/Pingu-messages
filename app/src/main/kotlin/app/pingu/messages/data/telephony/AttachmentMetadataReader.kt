package app.pingu.messages.data.telephony

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import app.pingu.messages.data.local.entity.AttachmentEntity
import app.pingu.messages.data.telephony.CursorUtils.longOr
import app.pingu.messages.data.telephony.CursorUtils.queryFirst
import app.pingu.messages.data.telephony.CursorUtils.stringOrNull

/**
 * Fills in the size, pixel dimensions and duration of an attachment.
 *
 * This costs an IPC and, for images, a header decode, so it is done for the messages of a thread
 * the user is actually looking at rather than during a bulk backfill of thousands of rows. The
 * numbers make the conversation feel solid: a photo bubble can reserve the right amount of space
 * before the image loads instead of jumping when it arrives.
 */
class AttachmentMetadataReader(private val context: Context) {

    fun enrich(attachment: AttachmentEntity): AttachmentEntity {
        val uri = runCatching { Uri.parse(attachment.uri) }.getOrNull() ?: return attachment
        val size = if (attachment.sizeBytes > 0) attachment.sizeBytes else sizeOf(uri)
        return when {
            attachment.mimeType.startsWith("image/", ignoreCase = true) -> {
                val (width, height) = imageDimensions(uri)
                attachment.copy(sizeBytes = size, width = width, height = height)
            }

            attachment.mimeType.startsWith("video/", ignoreCase = true) -> {
                val metadata = videoMetadata(uri)
                attachment.copy(
                    sizeBytes = size,
                    width = metadata.width,
                    height = metadata.height,
                    durationMillis = metadata.durationMillis,
                )
            }

            attachment.mimeType.startsWith("audio/", ignoreCase = true) ->
                attachment.copy(sizeBytes = size, durationMillis = audioDuration(uri))

            else -> attachment.copy(sizeBytes = size)
        }
    }

    /** Size, dimensions and duration of a file the user just picked, without touching the database. */
    data class MediaInfo(
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val durationMillis: Long,
    )

    fun inspect(uri: Uri, mimeType: String): MediaInfo {
        val size = sizeOf(uri)
        return when {
            mimeType.startsWith("image/", ignoreCase = true) -> {
                val (width, height) = imageDimensions(uri)
                MediaInfo(size, width, height, 0L)
            }

            mimeType.startsWith("video/", ignoreCase = true) -> {
                val metadata = videoMetadata(uri)
                MediaInfo(size, metadata.width, metadata.height, metadata.durationMillis)
            }

            mimeType.startsWith("audio/", ignoreCase = true) ->
                MediaInfo(size, 0, 0, audioDuration(uri))

            else -> MediaInfo(size, 0, 0, 0L)
        }
    }

    fun sizeOf(uri: Uri): Long {
        context.contentResolver.queryFirst(
            uri = uri,
            projection = arrayOf(OpenableColumns.SIZE),
            mapper = { it.longOr(OpenableColumns.SIZE, -1L) },
        )?.takeIf { it >= 0 }?.let { return it }

        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (error: Exception) {
            0L
        }
    }

    fun displayNameOf(uri: Uri): String? = context.contentResolver.queryFirst(
        uri = uri,
        projection = arrayOf(OpenableColumns.DISPLAY_NAME),
        mapper = { it.stringOrNull(OpenableColumns.DISPLAY_NAME) },
    )

    fun imageDimensions(uri: Uri): Pair<Int, Int> = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
    } catch (error: Exception) {
        Log.d(TAG, "Could not read image bounds for $uri", error)
        0 to 0
    }

    data class VideoMetadata(val width: Int, val height: Int, val durationMillis: Long)

    fun videoMetadata(uri: Uri): VideoMetadata = withRetriever(uri) { retriever ->
        VideoMetadata(
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0,
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0,
            durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L,
        )
    } ?: VideoMetadata(0, 0, 0L)

    fun audioDuration(uri: Uri): Long = withRetriever(uri) { retriever ->
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } ?: 0L

    private fun <T> withRetriever(uri: Uri, block: (MediaMetadataRetriever) -> T): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            block(retriever)
        } catch (error: Exception) {
            Log.d(TAG, "Could not read media metadata for $uri", error)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val TAG = "AttachmentMetadata"
    }
}
