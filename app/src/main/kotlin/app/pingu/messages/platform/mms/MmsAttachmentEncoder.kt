package app.pingu.messages.platform.mms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import app.pingu.messages.data.mms.pdu.MmsPart
import app.pingu.messages.data.mms.pdu.PduCharsets
import app.pingu.messages.data.mms.pdu.PduContentTypes
import app.pingu.messages.domain.model.Attachment
import java.io.ByteArrayOutputStream

/**
 * Turns composer attachments into MMS parts, shrinking images so the message fits what the carrier
 * will accept.
 *
 * Carriers reject an MMS that exceeds their size limit outright, and the limit is small: 300 kB is
 * common and 1 MB is generous. A modern phone photo is several megabytes, so sending one untouched
 * would fail every time. Images are therefore re-encoded at a progressively smaller scale until
 * they fit; video and other files are sent as they are, and the caller is told when they cannot
 * fit so the user gets a real explanation rather than a silent failure.
 */
class MmsAttachmentEncoder(private val context: Context) {

    sealed interface Result {
        data class Success(val parts: List<MmsPart>, val totalBytes: Int) : Result
        data class TooLarge(val limitBytes: Int, val actualBytes: Int) : Result
        data class Unreadable(val uri: String) : Result
    }

    /**
     * @param budgetBytes the carrier's maximum message size, minus headroom for headers.
     */
    fun encode(
        text: String?,
        attachments: List<Attachment>,
        budgetBytes: Int,
    ): Result {
        val parts = ArrayList<MmsPart>(attachments.size + 1)
        var used = 0

        if (!text.isNullOrEmpty()) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            used += bytes.size
            parts.add(
                MmsPart(
                    contentType = PduContentTypes.TEXT_PLAIN,
                    data = bytes,
                    fileName = "text.txt",
                    charsetMib = PduCharsets.UTF_8,
                ),
            )
        }

        val mediaBudget = (budgetBytes - used - HEADER_HEADROOM_BYTES).coerceAtLeast(0)
        val perAttachmentBudget =
            if (attachments.isEmpty()) 0 else mediaBudget / attachments.size

        for (attachment in attachments) {
            val uri = runCatching { Uri.parse(attachment.uri) }.getOrNull()
                ?: return Result.Unreadable(attachment.uri)

            val encoded = if (attachment.mimeType.startsWith("image/", ignoreCase = true) &&
                !attachment.mimeType.equals("image/gif", ignoreCase = true)
            ) {
                encodeImage(uri, perAttachmentBudget)
            } else {
                readBytes(uri)
            } ?: return Result.Unreadable(attachment.uri)

            if (encoded.size > perAttachmentBudget && perAttachmentBudget > 0) {
                return Result.TooLarge(budgetBytes, used + encoded.size)
            }

            used += encoded.size
            parts.add(
                MmsPart(
                    contentType = normalizeMimeType(attachment.mimeType, encoded),
                    data = encoded,
                    fileName = attachment.fileName,
                    name = attachment.fileName,
                ),
            )
        }

        if (used > budgetBytes) return Result.TooLarge(budgetBytes, used)
        return Result.Success(parts, used)
    }

    private fun readBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (error: Exception) {
        Log.w(TAG, "Could not read $uri", error)
        null
    }

    /**
     * Re-encodes an image as JPEG at the largest scale and quality that fits [budgetBytes].
     *
     * The first pass samples the bitmap down while decoding (cheap, and avoids decoding a 50 MP
     * image into memory), then quality is stepped down, and only if that is still not enough is the
     * bitmap scaled again.
     */
    private fun encodeImage(uri: Uri, budgetBytes: Int): ByteArray? {
        if (budgetBytes <= 0) return readBytes(uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not read image bounds for $uri", error)
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return readBytes(uri)

        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        val targetPixels = (budgetBytes.toLong() * PIXELS_PER_BYTE).coerceAtLeast(MIN_TARGET_PIXELS)
        var sampleSize = 1
        while (pixels / (sampleSize.toLong() * sampleSize) > targetPixels && sampleSize < 16) {
            sampleSize *= 2
        }

        var bitmap = decode(uri, sampleSize) ?: return null
        try {
            for (quality in QUALITY_STEPS) {
                val bytes = compress(bitmap, quality)
                if (bytes.size <= budgetBytes) return bytes
            }
            // Still too big: halve the dimensions and try the quality ladder once more.
            var attempts = 0
            while (attempts < MAX_DOWNSCALE_ATTEMPTS) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width / 2).coerceAtLeast(1),
                    (bitmap.height / 2).coerceAtLeast(1),
                    true,
                )
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
                for (quality in QUALITY_STEPS) {
                    val bytes = compress(bitmap, quality)
                    if (bytes.size <= budgetBytes) return bytes
                }
                attempts++
            }
            return compress(bitmap, QUALITY_STEPS.last())
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decode(uri: Uri, sampleSize: Int): Bitmap? = try {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (error: Exception) {
        Log.w(TAG, "Could not decode $uri", error)
        null
    } catch (error: OutOfMemoryError) {
        Log.w(TAG, "Ran out of memory decoding $uri")
        null
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /** Re-encoded images are always JPEG regardless of what the source claimed to be. */
    private fun normalizeMimeType(original: String, data: ByteArray): String = when {
        original.equals("image/gif", ignoreCase = true) -> original
        original.startsWith("image/", ignoreCase = true) && isJpeg(data) -> "image/jpeg"
        else -> original
    }

    private fun isJpeg(data: ByteArray): Boolean =
        data.size > 3 && (data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xFF) == 0xD8

    private companion object {
        const val TAG = "MmsAttachmentEncoder"

        /** Room left for PDU headers, SMIL and part headers. */
        const val HEADER_HEADROOM_BYTES = 4 * 1024

        /** Rough JPEG ratio used to pick an initial sample size. */
        const val PIXELS_PER_BYTE = 8L
        const val MIN_TARGET_PIXELS = 160L * 160L

        val QUALITY_STEPS = intArrayOf(85, 70, 55, 40, 25)
        const val MAX_DOWNSCALE_ATTEMPTS = 3
    }
}
