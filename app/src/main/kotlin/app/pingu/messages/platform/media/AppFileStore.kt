package app.pingu.messages.platform.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Private scratch storage plus the FileProvider that exposes individual files to other processes.
 *
 * Nothing here is world readable: files live in the app's cache directory and are only ever handed
 * out as `content://` URIs with a temporary, per-recipient grant. That is what lets the app share
 * an attachment with a gallery app, or hand an MMS PDU to the platform's messaging service, without
 * exporting a directory.
 */
class AppFileStore(private val context: Context) {

    private val authority: String get() = "${context.packageName}.fileprovider"

    fun cacheDirectory(name: String): File =
        File(context.cacheDir, name).apply { if (!exists()) mkdirs() }

    fun createCacheFile(directory: String, extension: String): File {
        val folder = cacheDirectory(directory)
        return File(folder, "${UUID.randomUUID()}$extension")
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    /**
     * Grants a package temporary access to a file URI.
     *
     * The platform's MMS service runs in another process and reads the outgoing PDU from a URI we
     * hand it, so it needs an explicit grant; the grant is released as soon as the transaction
     * finishes.
     */
    fun grantTo(packageName: String, uri: Uri, write: Boolean = false) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        runCatching { context.grantUriPermission(packageName, uri, flags) }
    }

    fun revokeFrom(uri: Uri) {
        runCatching {
            context.revokeUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun deleteQuietly(file: File?) {
        runCatching { file?.delete() }
    }

    /** Total bytes currently held in the app's caches, for the storage screen. */
    fun cacheSizeBytes(): Long = context.cacheDir.walkBottomUp()
        .filter { it.isFile }
        .sumOf { it.length() }

    fun clearCaches() {
        listOf(DIR_ATTACHMENTS, DIR_CAPTURES, DIR_MMS).forEach { name ->
            cacheDirectory(name).listFiles()?.forEach { it.delete() }
        }
    }

    companion object {
        const val DIR_ATTACHMENTS = "attachments"
        const val DIR_CAPTURES = "captures"
        const val DIR_VOICE = "voice"
        const val DIR_MMS = "mms"
        const val DIR_EXPORTS = "exports"

        /** The package that hosts the platform MMS service on essentially every Android build. */
        const val PLATFORM_PHONE_PACKAGE = "com.android.phone"
    }
}
