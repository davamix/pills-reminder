package io.github.davamix.pillsreminder.core.time

import io.github.davamix.pillsreminder.core.MADRID
import io.github.davamix.pillsreminder.core.at
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.schedule
import io.github.davamix.pillsreminder.core.schedule.PlannedTakes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone

// Europe/Madrid in 2026: on Sunday 29 March the clock jumps from 02:00 to 03:00 (CET, UTC+1, to CEST, UTC+2), and on
// Sunday 25 October it goes back from 03:00 to 02:00.
class ClockTimesTest {
    private val canary: ZoneId = ZoneId.of("Atlantic/Canary")

    private fun utc(
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Instant = at(month, day, hour, minute).toInstant(ZoneOffset.UTC)

    private fun hoursBetween(
        a: Instant,
        b: Instant,
    ): Long = Duration.between(a, b).toHours()

    @Test
    fun `a time in the spring gap falls due one hour later`() {
        assertEquals(utc(3, 29, 1, 30), at(3, 29, 2, 30).dueAt(MADRID))
        assertEquals(nowAt(at(3, 29, 3, 30)).toInstant(), at(3, 29, 2, 30).dueAt(MADRID))
    }

    @Test
    fun `a time in the repeated autumn hour falls due at the earlier one`() {
        assertEquals(utc(10, 25, 0, 30), at(10, 25, 2, 30).dueAt(MADRID))
    }

    @Test
    fun `every 8 hours across the spring change keeps 0800, and that interval is 7 real hours`() {
        val takes = PlannedTakes(schedule(at(3, 28, 16), 8, IntervalUnit.HOURS, MedicineDuration.Days(2)))

        assertEquals(at(3, 29, 0), takes[1])
        assertEquals(at(3, 29, 8), takes[2])
        assertEquals(7, hoursBetween(takes[1].dueAt(MADRID), takes[2].dueAt(MADRID)))
        assertEquals(8, hoursBetween(takes[2].dueAt(MADRID), takes[3].dueAt(MADRID)))
    }

    @Test
    fun `every 8 hours across the autumn change keeps 0800, and that interval is 9 real hours`() {
        val takes = PlannedTakes(schedule(at(10, 24, 16), 8, IntervalUnit.HOURS, MedicineDuration.Days(2)))

        assertEquals(at(10, 25, 0), takes[1])
        assertEquals(at(10, 25, 8), takes[2])
        assertEquals(9, hoursBetween(takes[1].dueAt(MADRID), takes[2].dueAt(MADRID)))
    }

    @Test
    fun `every hour through the spring gap, the 0200 take falls due with the 0300 one`() {
        val takes = PlannedTakes(schedule(at(3, 29, 1), 1, IntervalUnit.HOURS, MedicineDuration.Days(1)))

        assertEquals(at(3, 29, 2), takes[1])
        assertEquals(takes[1].dueAt(MADRID), takes[2].dueAt(MADRID))
        assertEquals(utc(3, 29, 1), takes[2].dueAt(MADRID))
    }

    @Test
    fun `every hour through the repeated hour, the 0200 take falls due at the first 0200`() {
        val takes = PlannedTakes(schedule(at(10, 25, 1), 1, IntervalUnit.HOURS, MedicineDuration.Days(1)))

        assertEquals(utc(10, 25, 0), takes[1].dueAt(MADRID))
        assertEquals(2, hoursBetween(takes[1].dueAt(MADRID), takes[2].dueAt(MADRID)))
    }

    @Test
    fun `every day at 0230 falls due at 0330 on the day of the gap only`() {
        val takes = PlannedTakes(schedule(at(3, 28, 2, 30), 1, IntervalUnit.DAYS, MedicineDuration.Days(3)))

        assertEquals(nowAt(at(3, 28, 2, 30)).toInstant(), takes[0].dueAt(MADRID))
        assertEquals(nowAt(at(3, 29, 3, 30)).toInstant(), takes[1].dueAt(MADRID))
        assertEquals(nowAt(at(3, 30, 2, 30)).toInstant(), takes[2].dueAt(MADRID))
    }

    @Test
    fun `counts don't change across daylight saving`() {
        for (start in listOf(at(3, 25, 8), at(10, 21, 8))) {
            val takes = PlannedTakes(schedule(start, 8, IntervalUnit.HOURS, MedicineDuration.Days(7)))

            assertEquals(21L, takes.count, "from $start")
            assertEquals(start.plusDays(6).plusHours(16), takes.last, "from $start")
        }
    }

    @Test
    fun `after a change of zone, takes keep their clock times and fall due at that time in the new zone`() {
        val take = at(11, 23, 16)

        assertEquals(nowAt(take, MADRID).toInstant(), take.dueAt(MADRID))
        assertEquals(nowAt(take, canary).toInstant(), take.dueAt(canary))
        assertEquals(1, hoursBetween(take.dueAt(MADRID), take.dueAt(canary)))
    }

    @Test
    fun `the suggested first take is the next whole hour`() {
        assertEquals(at(11, 23, 11), suggestedFirstTake(nowAt(at(11, 23, 10, 30))))
        assertEquals(at(11, 23, 11), suggestedFirstTake(nowAt(at(11, 23, 10))))
        assertEquals(at(11, 24, 0), suggestedFirstTake(nowAt(at(11, 23, 23, 30))))
    }

    @Test
    fun `the suggested first take skips the hour missing in the spring gap`() {
        assertEquals(at(3, 29, 3), suggestedFirstTake(nowAt(at(3, 29, 1, 30))))
    }

    @Test
    fun `in the repeated hour the suggested first take is 0300, which falls due after now`() {
        val firstPass = ZonedDateTime.ofInstant(utc(10, 25, 0, 30), MADRID)
        val secondPass = ZonedDateTime.ofInstant(utc(10, 25, 1, 30), MADRID)

        assertEquals(at(10, 25, 3), suggestedFirstTake(firstPass))
        assertEquals(at(10, 25, 3), suggestedFirstTake(secondPass))
    }

    @Test
    fun `CurrentZoneClock follows a change of the default zone`() {
        val original = TimeZone.getDefault()
        try {
            val clock = CurrentZoneClock()
            TimeZone.setDefault(TimeZone.getTimeZone(MADRID))
            assertEquals(MADRID, clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone(canary))
            assertEquals(canary, clock.zone)
            assertNotEquals(MADRID, ZonedDateTime.now(clock).zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
