package io.github.davamix.pillsreminder.ui.common

import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The tick at each whole minute that the screens work out their states again at (P6-D3). */
class MinuteTicksTest {
    /** A clock that runs with the test's virtual time, from [start], plus an [offset] the test can change. */
    private class SchedulerClock(
        private val start: Instant,
        private val scheduler: TestCoroutineScheduler,
    ) : Clock() {
        var offsetMillis = 0L

        override fun getZone(): ZoneId = ZoneId.of("Europe/Madrid")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = start.plusMillis(scheduler.currentTime + offsetMillis)
    }

    private fun at(local: LocalDateTime): Instant = nowAt(local).toInstant()

    @Test
    fun `it ticks at once, then at each whole minute of the clock`() =
        runTest {
            val clock = SchedulerClock(at(nov(23, 10)).plusSeconds(30), testScheduler)
            val ticks = mutableListOf<Instant>()
            backgroundScope.launch { minuteTicks(clock).collect { ticks += clock.instant() } }

            advanceTimeBy(150_000)
            runCurrent()

            val expected = listOf(at(nov(23, 10)).plusSeconds(30), at(nov(23, 10, 1)), at(nov(23, 10, 2)), at(nov(23, 10, 3)))
            assertEquals(expected, ticks)
        }

    @Test
    fun `after the phone's time changes, the next wait is worked out from the new time`() =
        runTest {
            val clock = SchedulerClock(at(nov(23, 10)).plusSeconds(30), testScheduler)
            val ticks = mutableListOf<Instant>()
            backgroundScope.launch { minuteTicks(clock).collect { ticks += clock.instant() } }
            runCurrent()

            // 10 s later, the phone's time moves 20 s ahead: the wait already started still ends 30 s after the first
            // tick, and the next one is back on the minute.
            advanceTimeBy(10_000)
            clock.offsetMillis = 20_000
            advanceTimeBy(60_000)
            runCurrent()

            val expected = listOf(at(nov(23, 10)).plusSeconds(30), at(nov(23, 10, 1)).plusSeconds(20), at(nov(23, 10, 2)))
            assertEquals(expected, ticks)
        }
}
