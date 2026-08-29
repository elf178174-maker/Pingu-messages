package app.pingu.messages.core.time

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A named point in the near future offered by the scheduling dialog. */
data class SchedulePreset(val label: String, val dateTime: LocalDateTime) {
    fun epochMillis(zone: ZoneId): Long = dateTime.atZone(zone).toInstant().toEpochMilli()
}

/** Localised labels for the presets, supplied by the UI. */
data class ScheduleLabels(
    val inOneHour: String,
    val thisEvening: String,
    val tomorrowMorning: String,
    val mondayMorning: String,
)

/**
 * The scheduling presets.
 *
 * Kept as a pure function so the rule that matters - a preset in the past is never offered - is
 * unit tested rather than discovered by a user whose "this evening" message went out immediately.
 */
object SchedulePresets {

    val EVENING: LocalTime = LocalTime.of(19, 0)
    val MORNING: LocalTime = LocalTime.of(9, 0)

    fun forNow(now: LocalDateTime, labels: ScheduleLabels): List<SchedulePreset> {
        val candidates = listOf(
            SchedulePreset(labels.inOneHour, now.plusHours(1)),
            SchedulePreset(labels.thisEvening, LocalDateTime.of(now.toLocalDate(), EVENING)),
            SchedulePreset(
                labels.tomorrowMorning,
                LocalDateTime.of(now.toLocalDate().plusDays(1), MORNING),
            ),
            SchedulePreset(
                labels.mondayMorning,
                LocalDateTime.of(
                    now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                    MORNING,
                ),
            ),
        )
        return candidates.filter { it.dateTime.isAfter(now) }
    }
}
