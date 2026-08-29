package app.pingu.messages.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Classification of a timestamp relative to "now", kept free of Android and of string resources so
 * it can be unit tested and reused by the widget, the notification builder and the UI alike.
 */
enum class TimeBucket {
    /** Same calendar day. */
    TODAY,

    /** The calendar day before today. */
    YESTERDAY,

    /** Within the last seven days but older than yesterday. */
    THIS_WEEK,

    /** Same calendar year, older than a week. */
    THIS_YEAR,

    /** Anything older. */
    OLDER,
}

object RelativeTime {

    fun bucket(epochMillis: Long, now: Long, zone: ZoneId): TimeBucket {
        val date = toLocalDate(epochMillis, zone)
        val today = toLocalDate(now, zone)
        val daysBetween = ChronoUnit.DAYS.between(date, today)
        return when {
            daysBetween <= 0L -> TimeBucket.TODAY
            daysBetween == 1L -> TimeBucket.YESTERDAY
            daysBetween < 7L -> TimeBucket.THIS_WEEK
            date.year == today.year -> TimeBucket.THIS_YEAR
            else -> TimeBucket.OLDER
        }
    }

    /** True when two timestamps fall on the same calendar day, used for date separators. */
    fun isSameDay(first: Long, second: Long, zone: ZoneId): Boolean =
        toLocalDate(first, zone) == toLocalDate(second, zone)

    /**
     * True when [candidate] is close enough to [previous] that repeating the header would be
     * noise. Messages from the same sender inside this window are grouped into one visual block.
     */
    fun withinGroupingWindow(previous: Long, candidate: Long, windowMillis: Long): Boolean =
        candidate >= previous && candidate - previous <= windowMillis

    fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
