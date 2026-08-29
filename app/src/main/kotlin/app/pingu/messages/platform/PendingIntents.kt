package app.pingu.messages.platform

import android.app.PendingIntent
import android.os.Build

/**
 * PendingIntent flag helpers.
 *
 * SMS result callbacks have to be **mutable**: the platform fills the delivery report PDU and the
 * error code into the intent before sending it back, and an immutable PendingIntent silently drops
 * them, which is how delivery reports quietly stop working. Everything else the app creates is
 * immutable, which is the safe default.
 */
object PendingIntents {

    val immutable: Int
        get() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    val mutable: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    /** Request codes must be unique per pending callback or the intents overwrite each other. */
    private var requestCodeCounter = 0

    @Synchronized
    fun nextRequestCode(): Int {
        requestCodeCounter = (requestCodeCounter + 1) and 0x0FFF_FFFF
        return requestCodeCounter
    }
}
