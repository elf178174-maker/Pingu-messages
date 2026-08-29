package app.pingu.messages.platform.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.TypedValue
import app.pingu.messages.core.util.Avatars
import kotlin.math.min

/**
 * Small circular avatars for notifications and the widget, where Compose is not available.
 *
 * A contact photo is used when there is one; otherwise the same initials-on-colour avatar the app
 * draws in Compose is rendered onto a bitmap, so a person looks the same in the notification shade
 * as they do in the conversation list.
 */
class AvatarBitmaps(private val context: Context) {

    private val palette = intArrayOf(
        0xFF00696E.toInt(),
        0xFF3F5F90.toInt(),
        0xFF6A5A8F.toInt(),
        0xFF8A5340.toInt(),
        0xFF3F6B4A.toInt(),
        0xFF7A5B1F.toInt(),
        0xFF8C4F63.toInt(),
        0xFF4C6358.toInt(),
    )

    fun forRecipient(displayName: String?, identityKey: String, photoUri: String?): Bitmap {
        photoUri?.let { uri ->
            loadPhoto(uri)?.let { return circleCrop(it) }
        }
        return letterAvatar(Avatars.initials(displayName), Avatars.colorSlot(identityKey))
    }

    private fun loadPhoto(uri: String): Bitmap? = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (error: Exception) {
        null
    } catch (error: OutOfMemoryError) {
        null
    }

    private fun circleCrop(source: Bitmap): Bitmap {
        val size = min(source.width, source.height).coerceAtLeast(1)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2
        canvas.drawBitmap(
            source,
            Rect(left, top, left + size, top + size),
            Rect(0, 0, size, size),
            paint,
        )
        return output
    }

    private fun letterAvatar(initials: String, colorSlot: Int): Bitmap {
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            AVATAR_SIZE_DP,
            context.resources.displayMetrics,
        ).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette[colorSlot % palette.size]
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, background)

        if (initials.isNotEmpty()) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = sizePx * 0.42f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val metrics = text.fontMetrics
            val baseline = sizePx / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(initials, sizePx / 2f, baseline, text)
        }
        return bitmap
    }

    private companion object {
        const val AVATAR_SIZE_DP = 48f
    }
}
