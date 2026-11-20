package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.LocalTime

class PlannedTakesTest {
    private val amoxicillin = PlannedTakes(Example.amoxicillin.schedule)
    private val atrovent = PlannedTakes(Example.atrovent.schedule)

    private fun PlannedTakes.all(): List<LocalDateTime> = (0 until count!!).map { get(it) }

    @Test
    fun `the example has 21 and 15 takes, the last on Mon 30 Nov and Sat 28 Nov at midnight`() {
        assertEquals(21L, amoxicillin.count)
        assertEquals(nov(30, 0), amoxicillin.last)
        assertEquals(15L, atrovent.count)
        assertEquals(nov(28, 0), atrovent.last)
    }

    @Test
    fun `the example's takes are at 0800, 1600 and 0000 each day`() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(0, 0))

        for (takes in listOf(amoxicillin, atrovent)) {
            takes.all().forEachIndexed { index, take -> assertEquals(times[index % 3], take.toLocalTime(), "take $index") }
        }
    }

    @Test
    fun `the first take is at the first-take time, and each take one interval after the one before`() {
        val takes = amoxicillin.all()

        assertEquals(nov(23, 8), takes.first())
        takes.zipWithNext().forEach { (a, b) -> assertEquals(a.plusHours(8), b) }
    }

    @Test
    fun `every 5 hours for 1 day gives 5 takes, rounding up`() {
        val takes = PlannedTakes(schedule(nov(23, 0), 5, IntervalUnit.HOURS, MedicineDuration.Days(1)))

        assertEquals(listOf(0, 5, 10, 15, 20).map { nov(23, it) }, takes.all())
    }

    @Test
    fun `every 2 days for 7 days gives 4 takes, on days 0, 2, 4 and 6`() {
        val takes = PlannedTakes(schedule(nov(23, 8), 2, IntervalUnit.DAYS, MedicineDuration.Days(7)))

        assertEquals(listOf(23, 25, 27, 29).map { nov(it, 8) }, takes.all())
    }

    @Test
    fun `every 2 days from Monday at 0800 keeps the time of day, on Wednesday and Friday`() {
        val takes = PlannedTakes(schedule(nov(23, 8), 2, IntervalUnit.DAYS, MedicineDuration.Days(5)))

        assertEquals(listOf(nov(23, 8), nov(25, 8), nov(27, 8)), takes.all())
    }

    @Test
    fun `a week is 7 days, so every day for 12 weeks gives 84 takes`() {
        val takes = PlannedTakes(schedule(nov(23, 8), 1, IntervalUnit.DAYS, MedicineDuration.Weeks(12)))

        assertEquals(84L, takes.count)
        assertEquals(nov(23, 8).plusDays(83), takes.last)
    }

    @Test
    fun `every week for 12 weeks gives 12 takes`() {
        val takes = PlannedTakes(schedule(nov(23, 8), 1, IntervalUnit.WEEKS, MedicineDuration.Weeks(12)))

        assertEquals(12L, takes.count)
        assertEquals(nov(23, 8).plusWeeks(11), takes.last)
    }

    @Test
    fun `an interval longer than the duration gives a single take`() {
        val takes = PlannedTakes(schedule(nov(23, 8), 2, IntervalUnit.WEEKS, MedicineDuration.Days(3)))

        assertEquals(listOf(nov(23, 8)), takes.all())
    }

    @Test
    fun `with no end there is no count and no last take, and a take ten years ahead is worked out directly`() {
        val takes = PlannedTakes(schedule(nov(23, 8), 1, IntervalUnit.WEEKS, MedicineDuration.NoEnd))
        val tenYears = nov(23, 8).plusYears(10)

        assertNull(takes.count)
        assertNull(takes.last)
        assertEquals(nov(23, 8).plusWeeks(522), takes[522])
        val next = takes.indexOfFirstAfter(tenYears)!!
        assertTrue(takes[next] > tenYears && takes[next - 1] <= tenYears)
    }

    @Test
    fun `the first take after a time is strictly after it, also with seconds`() {
        assertEquals(0L, amoxicillin.indexOfFirstAfter(nov(22, 12)))
        assertEquals(1L, amoxicillin.indexOfFirstAfter(nov(23, 8)))
        assertEquals(1L, amoxicillin.indexOfFirstAfter(nov(23, 15, 59).withSecond(59)))
        assertEquals(2L, amoxicillin.indexOfFirstAfter(nov(23, 16).withSecond(1)))
        assertEquals(20L, amoxicillin.indexOfFirstAfter(nov(29, 23, 59)))
        assertNull(amoxicillin.indexOfFirstAfter(nov(30, 0)))
    }

    @Test
    fun `the index of a planned time, and none for a time that isn't planned`() {
        assertEquals(0L, amoxicillin.indexOf(nov(23, 8)))
        assertEquals(20L, amoxicillin.indexOf(nov(30, 0)))
        assertNull(amoxicillin.indexOf(nov(23, 9)))
        assertNull(amoxicillin.indexOf(nov(23, 0)))
        assertNull(amoxicillin.indexOf(nov(30, 8)))
        assertNull(amoxicillin.indexOf(nov(23, 16).withSecond(30)))
    }

    @Test
    fun `a take outside the schedule is refused`() {
        assertThrows<IllegalArgumentException> { amoxicillin[-1] }
        assertThrows<IllegalArgumentException> { amoxicillin[21] }
    }

    @Test
    fun `the largest amounts don't overflow`() {
        val one = PlannedTakes(schedule(nov(23, 8), Int.MAX_VALUE, IntervalUnit.WEEKS, MedicineDuration.Weeks(Int.MAX_VALUE)))
        val most = PlannedTakes(schedule(nov(23, 8), 1, IntervalUnit.MINUTES, MedicineDuration.Weeks(Int.MAX_VALUE)))

        assertEquals(1L, one.count)
        assertEquals(nov(23, 8), one.last)
        assertEquals(Int.MAX_VALUE * 10_080L, most.count)
        assertEquals(nov(23, 8).plusWeeks(Int.MAX_VALUE.toLong()).minusMinutes(1), most.last)
        assertEquals(most.count!! - 1, most.indexOfFirstAfter(most.last!!.minusMinutes(1)))
    }
}
