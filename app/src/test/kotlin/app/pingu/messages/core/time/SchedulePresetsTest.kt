package app.pingu.messages.core.time

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Test

class SchedulePresetsTest {

    private val labels = ScheduleLabels(
        inOneHour = "In 1 hour",
        thisEvening = "This evening",
        tomorrowMorning = "Tomorrow morning",
        mondayMorning = "Monday morning",
    )

    @Test
    fun `a morning offers all four presets`() {
        val presets = SchedulePresets.forNow(LocalDateTime.of(2026, 3, 11, 9, 0), labels)
        assertThat(presets.map { it.label })
            .containsExactly("In 1 hour", "This evening", "Tomorrow morning", "Monday morning")
    }

    @Test
    fun `this evening disappears once the evening has passed`() {
        val presets = SchedulePresets.forNow(LocalDateTime.of(2026, 3, 11, 21, 0), labels)
        assertThat(presets.map { it.label }).doesNotContain("This evening")
        assertThat(presets.map { it.label }).contains("Tomorrow morning")
    }

    @Test
    fun `every preset is in the future`() {
        val now = LocalDateTime.of(2026, 3, 11, 18, 55)
        SchedulePresets.forNow(now, labels).forEach { preset ->
            assertThat(preset.dateTime.isAfter(now)).isTrue()
        }
    }

    @Test
    fun `monday morning is the next monday, never today`() {
        val monday = LocalDateTime.of(2026, 3, 9, 8, 0)
        val preset = SchedulePresets.forNow(monday, labels).first { it.label == "Monday morning" }
        assertThat(preset.dateTime.toLocalDate()).isEqualTo(monday.toLocalDate().plusDays(7))
    }
}
