package app.pingu.messages.core.time

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class RelativeTimeTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val now = LocalDateTime.of(2026, 3, 15, 14, 30).atZone(zone).toInstant().toEpochMilli()

    private fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `today is today even at one minute past midnight`() {
        val bucket = RelativeTime.bucket(
            at(LocalDate.of(2026, 3, 15), LocalTime.of(0, 1)),
            now,
            zone,
        )
        assertThat(bucket).isEqualTo(TimeBucket.TODAY)
    }

    @Test
    fun `late yesterday is yesterday, not today`() {
        val bucket = RelativeTime.bucket(
            at(LocalDate.of(2026, 3, 14), LocalTime.of(23, 59)),
            now,
            zone,
        )
        assertThat(bucket).isEqualTo(TimeBucket.YESTERDAY)
    }

    @Test
    fun `earlier this week falls in the week bucket`() {
        assertThat(RelativeTime.bucket(at(LocalDate.of(2026, 3, 11)), now, zone))
            .isEqualTo(TimeBucket.THIS_WEEK)
    }

    @Test
    fun `older dates in the same year use the year bucket`() {
        assertThat(RelativeTime.bucket(at(LocalDate.of(2026, 1, 2)), now, zone))
            .isEqualTo(TimeBucket.THIS_YEAR)
    }

    @Test
    fun `previous years are older`() {
        assertThat(RelativeTime.bucket(at(LocalDate.of(2024, 12, 31)), now, zone))
            .isEqualTo(TimeBucket.OLDER)
    }

    @Test
    fun `same day comparison respects the time zone`() {
        val morning = at(LocalDate.of(2026, 3, 15), LocalTime.of(1, 0))
        val evening = at(LocalDate.of(2026, 3, 15), LocalTime.of(23, 0))
        assertThat(RelativeTime.isSameDay(morning, evening, zone)).isTrue()
        assertThat(RelativeTime.isSameDay(morning, at(LocalDate.of(2026, 3, 16)), zone)).isFalse()
    }

    @Test
    fun `messages close together are grouped`() {
        val window = 3 * 60 * 1000L
        assertThat(RelativeTime.withinGroupingWindow(now, now + 60_000, window)).isTrue()
        assertThat(RelativeTime.withinGroupingWindow(now, now + 10 * 60_000, window)).isFalse()
    }
}
