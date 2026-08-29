package app.pingu.messages.core.util

import java.util.Locale
import kotlin.math.abs

/** Human readable byte counts using the decimal units Android surfaces elsewhere (kB, MB, GB). */
object FileSizes {

    private val units = arrayOf("kB", "MB", "GB", "TB")

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (abs(bytes) < 1000) return "$bytes B"
        var value = bytes.toDouble()
        var unitIndex = -1
        while (abs(value) >= 1000 && unitIndex < units.lastIndex) {
            value /= 1000.0
            unitIndex++
        }
        val pattern = if (abs(value) >= 100 || unitIndex == 0) "%.0f %s" else "%.1f %s"
        return String.format(locale, pattern, value, units[unitIndex])
    }
}
