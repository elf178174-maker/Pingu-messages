package app.pingu.messages.ui.util

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.pingu.messages.R
import app.pingu.messages.core.time.RelativeTime
import app.pingu.messages.core.time.TimeBucket
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date and time formatting for the UI.
 *
 * The clock format follows the system 12/24-hour setting rather than the locale default, because
 * that is the switch users actually change. Everything else uses the locale's own patterns.
 */
object TimeFormatting {

    fun clockPattern(context: Context): String =
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"

    fun time(context: Context, epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        format(clockPattern(context), epochMillis, zone)

    fun dayAndTime(
        context: Context,
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = format("EEE d MMM, ${clockPattern(context)}", epochMillis, zone)

    fun fullDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        format("EEEE d MMMM yyyy", epochMillis, zone)

    fun format(pattern: String, epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .withZone(zone)
            .format(Instant.ofEpochMilli(epochMillis))
}

/**
 * The compact timestamp shown in the conversation list: a time today, a weekday this week, a date
 * beyond that. Exactly enough to place a message without taking space from the message itself.
 */
@Composable
fun rememberListTimestamp(epochMillis: Long): String {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    return remember(epochMillis, now / TIMESTAMP_REFRESH_MILLIS) {
        if (epochMillis <= 0L) {
            ""
        } else {
            val zone = ZoneId.systemDefault()
            when (RelativeTime.bucket(epochMillis, now, zone)) {
                TimeBucket.TODAY -> TimeFormatting.time(context, epochMillis, zone)
                TimeBucket.YESTERDAY, TimeBucket.THIS_WEEK ->
                    TimeFormatting.format("EEE", epochMillis, zone)

                TimeBucket.THIS_YEAR -> TimeFormatting.format("d MMM", epochMillis, zone)
                TimeBucket.OLDER -> TimeFormatting.format("dd/MM/yy", epochMillis, zone)
            }
        }
    }
}

/** The header shown above the first message of each day. */
@Composable
fun rememberDateSeparatorLabel(epochMillis: Long): String {
    val today = stringResource(R.string.date_today)
    val yesterday = stringResource(R.string.date_yesterday)
    val now = System.currentTimeMillis()
    return remember(epochMillis, today, yesterday, now / TIMESTAMP_REFRESH_MILLIS) {
        val zone = ZoneId.systemDefault()
        when (RelativeTime.bucket(epochMillis, now, zone)) {
            TimeBucket.TODAY -> today
            TimeBucket.YESTERDAY -> yesterday
            TimeBucket.THIS_WEEK -> TimeFormatting.format("EEEE", epochMillis, zone)
            TimeBucket.THIS_YEAR -> TimeFormatting.format("EEEE d MMMM", epochMillis, zone)
            TimeBucket.OLDER -> TimeFormatting.fullDate(epochMillis, zone)
        }
    }
}

@Composable
fun rememberMessageTime(epochMillis: Long): String {
    val context = LocalContext.current
    return remember(epochMillis) { TimeFormatting.time(context, epochMillis) }
}

/** Duration as m:ss, for voice messages and video attachments. */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

/** Recomputing once a minute is enough for "today" to become "yesterday" without churn. */
private const val TIMESTAMP_REFRESH_MILLIS = 60_000L
