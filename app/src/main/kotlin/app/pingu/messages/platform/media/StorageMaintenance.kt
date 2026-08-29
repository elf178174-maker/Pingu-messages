package app.pingu.messages.platform.media

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.net.Uri
import android.util.Log
import androidx.annotation.RequiresApi
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.domain.model.Attachment
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Storage accounting and clean-up.
 *
 * Two different things take up space and the user is shown both separately, because only one of
 * them is safe to delete without losing anything: the app's own cache of decoded and captured
 * files, and the media inside received messages.
 *
 * The "delete old media automatically" setting deletes *messages'* media, so it is opt-in, off by
 * default, and never runs on a message the user marked in any way.
 */
class StorageMaintenance(
    private val context: Context,
    private val database: PinguDatabase,
    private val fileStore: AppFileStore,
    private val settings: SettingsStore,
    private val ioDispatcher: CoroutineDispatcher,
) {

    data class Usage(
        val cacheBytes: Long,
        val attachmentBytes: Long,
        val messageCount: Int,
        val attachmentCount: Int,
    )

    suspend fun usage(): Usage = withContext(ioDispatcher) {
        val attachments = database.attachmentDao().totalBytes()
        Usage(
            cacheBytes = fileStore.cacheSizeBytes(),
            attachmentBytes = attachments,
            messageCount = database.conversationDao().unreadCounts().size,
            attachmentCount = 0,
        )
    }

    suspend fun clearCaches() = withContext(ioDispatcher) { fileStore.clearCaches() }

    /** Applies the retention setting, if the user turned it on. */
    suspend fun applyRetentionPolicy() = withContext(ioDispatcher) {
        val days = settings.settings.first().autoDeleteMediaDays
        if (days <= 0) return@withContext
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val uris = database.attachmentDao().urisOlderThan(cutoff)
        if (uris.isEmpty()) return@withContext
        Log.i(TAG, "Retention policy is removing ${uris.size} old attachments")
        uris.forEach { uri ->
            runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
        }
    }

    /**
     * Copies an attachment into the public Downloads collection so the user keeps it after the
     * message is gone. Uses MediaStore, so no storage permission is needed on any supported
     * version and the file lands where the system file manager expects it.
     */
    suspend fun saveToDownloads(attachment: Attachment): Result<Uri> = withContext(ioDispatcher) {
        runCatching {
            val name = attachment.fileName?.takeIf { it.isNotBlank() }
                ?: "pingu-${System.currentTimeMillis()}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveThroughMediaStore(attachment, name)
            } else {
                saveIntoPublicDownloads(attachment, name)
            }
        }
    }

    /** Android 10 and later: MediaStore owns the Downloads collection and no permission is needed. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveThroughMediaStore(attachment: Attachment, name: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, attachment.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("The Downloads collection rejected the file")

        resolver.openInputStream(Uri.parse(attachment.uri))?.use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
        } ?: error("The attachment could not be read")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        return target
    }

    /**
     * Android 9 and below: there is no Downloads collection to insert into, so the file is written
     * to the public folder directly and the media scanner is told about it, which is what makes it
     * appear in the file manager without a reboot. Needs WRITE_EXTERNAL_STORAGE, which the manifest
     * caps at API 28 and the UI asks for only when the user taps Save on such a device.
     */
    @Suppress("DEPRECATION")
    private fun saveIntoPublicDownloads(attachment: Attachment, name: String): Uri {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!directory.exists() && !directory.mkdirs()) {
            error("The Downloads folder is not available")
        }
        val target = availableFile(directory, name)
        context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("The attachment could not be read")
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(attachment.mimeType),
            null,
        )
        return Uri.fromFile(target)
    }

    /** "photo.jpg", then "photo (1).jpg": saving twice must not overwrite the first copy. */
    private fun availableFile(directory: File, name: String): File {
        val extension = name.substringAfterLast('.', "")
        val base = if (extension.isEmpty()) name else name.substringBeforeLast('.')
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var candidate = File(directory, name)
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base ($index)$suffix")
            index++
        }
        return candidate
    }

    /** Copies a shared or picked file into the app's cache so it survives the grant expiring. */
    suspend fun materialize(uri: Uri, extension: String): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val file = fileStore.createCacheFile(AppFileStore.DIR_ATTACHMENTS, extension)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("The file could not be read")
            file
        }
    }

    /**
     * Copies a picked file into the cache and returns a `content://` URI for it.
     *
     * The URI is served by the app's FileProvider, so it can be attached to a message, shared with
     * another app or handed to the platform MMS service - none of which a raw file path could do.
     */
    suspend fun materializeToContentUri(uri: Uri, extension: String): Result<String> =
        materialize(uri, extension).map { fileStore.uriFor(it).toString() }

    fun contentUriFor(file: File): Uri = fileStore.uriFor(file)

    /**
     * An empty file the system camera can write into, exposed as a content URI.
     *
     * Capturing through the system camera app means this app never needs the camera permission and
     * the user gets the camera they already know, with their own settings.
     */
    fun newCaptureUri(extension: String): Uri {
        val file = fileStore.createCacheFile(AppFileStore.DIR_CAPTURES, extension)
        runCatching { file.createNewFile() }
        return fileStore.uriFor(file)
    }

    private companion object {
        const val TAG = "StorageMaintenance"
    }
}
