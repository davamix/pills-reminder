package io.github.davamix.pillsreminder.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The time left to a take, in whole minutes (HM-1, P7 SPEC §4.3.3). */
class TimeLeftTest {
    private val now: Instant = Instant.parse("2026-11-23T09:30:00Z")
    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")

    private fun after(duration: Duration) = TimeLeft.between(now, now.plus(duration))

    private fun before(duration: Duration) = TimeLeft.between(now, now.minus(duration))

    private fun ahead(
        days: Long,
        hours: Int,
        minutes: Int,
    ) = TimeLeft(past = false, days = days, hours = hours, minutes = minutes)

    private fun ago(
        days: Long,
        hours: Int,
        minutes: Int,
    ) = TimeLeft(past = true, days = days, hours = hours, minutes = minutes)

    @Test
    fun `a take 20 seconds ahead is 1 minute ahead, never 0`() {
        assertEquals(ahead(0, 0, 1), after(Duration.ofSeconds(20)))
        assertEquals(ahead(0, 0, 1), after(Duration.ofMillis(1)))
    }

    @Test
    fun `ahead rounds up to whole minutes`() {
        assertEquals(ahead(0, 5, 30), after(Duration.ofHours(5).plusMinutes(29).plusMillis(59_800)))
        assertEquals(ahead(0, 5, 30), after(Duration.ofHours(5).plusMinutes(30)))
        assertEquals(ahead(0, 5, 31), after(Duration.ofHours(5).plusMinutes(30).plusSeconds(1)))
    }

    @Test
    fun `whole hours have no minutes, and minutes alone no hours`() {
        assertEquals(ahead(0, 7, 0), after(Duration.ofHours(7)))
        assertEquals(ahead(0, 0, 25), after(Duration.ofMinutes(25)))
        assertEquals(ahead(0, 1, 0), after(Duration.ofMinutes(60)))
    }

    @Test
    fun `from a day on, it counts days, hours and minutes`() {
        assertEquals(ahead(1, 7, 55), after(Duration.ofHours(31).plusMinutes(55)))
        assertEquals(ahead(1, 0, 0), after(Duration.ofHours(24)))
        assertEquals(ahead(6, 23, 59), after(Duration.ofDays(7).minusMinutes(1)))
    }

    @Test
    fun `exactly at its planned instant a take is past, with nothing left`() {
        assertEquals(ago(0, 0, 0), TimeLeft.between(now, now))
    }

    @Test
    fun `past rounds down, so under a minute after is still nothing`() {
        assertEquals(ago(0, 0, 0), before(Duration.ofSeconds(59)))
        assertEquals(ago(0, 0, 5), before(Duration.ofMinutes(5).plusSeconds(59)))
        assertEquals(ago(0, 1, 10), before(Duration.ofMinutes(70)))
        assertEquals(
            ago(2, 3, 10),
            before(
                Duration
                    .ofDays(2)
                    .plusHours(3)
                    .plusMinutes(10)
                    .plusSeconds(30),
            ),
        )
    }

    @Test
    fun `the night the clocks go back has an hour more`() {
        val from = LocalDateTime.of(2026, 10, 25, 0, 30).atZone(madrid).toInstant()
        val to = LocalDateTime.of(2026, 10, 25, 8, 0).atZone(madrid).toInstant()

        assertEquals(ahead(0, 8, 30), TimeLeft.between(from, to))
    }

    @Test
    fun `the night the clocks go forward has an hour less`() {
        val from = LocalDateTime.of(2026, 3, 29, 0, 30).atZone(madrid).toInstant()
        val to = LocalDateTime.of(2026, 3, 29, 8, 0).atZone(madrid).toInstant()

        assertEquals(ahead(0, 6, 30), TimeLeft.between(from, to))
    }
}
