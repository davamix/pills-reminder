package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.EarlierSchedule
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MedicineTakesTest {
    private val everyEightHours = Example.amoxicillin.schedule

    /** Spec §4.4's Amoxicillin, changed on Tue 24 Nov at 10:00 to every 12 h from the same first take (SPEC §4.3.6). */
    private val edited: Medicine =
        Example.amoxicillin.copy(
            schedule = schedule(nov(23, 8), 12, IntervalUnit.HOURS, MedicineDuration.Days(7)),
            earlierSchedules = listOf(EarlierSchedule(everyEightHours, until = nov(24, 10))),
        )

    private fun MedicineTakes.planned(): List<LocalDateTime> =
        takes(LocalDateTime.MIN, LocalDateTime.MAX, nowAt(nov(23, 12))).map { it.key.plannedAt }.toList()

    @Test
    fun `an edited medicine keeps its earlier takes up to the edit, and the new schedule's after it`() {
        val takes = MedicineTakes(edited, null, emptyMap())
        val newTakes = listOf(nov(24, 20)) + (25..29).flatMap { listOf(nov(it, 8), nov(it, 20)) }

        assertEquals(listOf(nov(23, 8), nov(23, 16), nov(24, 0), nov(24, 8)) + newTakes, takes.planned())
        assertEquals(15L, takes.count)
        assertEquals(nov(23, 8), takes.first)
        assertEquals(nov(29, 20), takes.last)
    }

    @Test
    fun `the take after an earlier schedule's last one comes from the new schedule`() {
        val takes = MedicineTakes(edited, null, emptyMap())

        assertEquals(nov(24, 20), takes.nextAfter(nov(24, 8)))
        assertEquals(nov(24, 8), takes.latestAtOrBefore(nov(24, 19)))
        assertNull(takes.take(nov(24, 16), nowAt(nov(24, 12))), "the old schedule's take after the edit is gone")
    }

    @Test
    fun `the last take of an earlier schedule becomes Missed when the next take falls due, if that comes first`() {
        val weekly = schedule(nov(23, 8), 1, IntervalUnit.WEEKS, MedicineDuration.NoEnd)
        val medicine =
            Example.amoxicillin.copy(
                schedule = schedule(nov(23, 8), 8, IntervalUnit.HOURS, MedicineDuration.NoEnd),
                earlierSchedules = listOf(EarlierSchedule(weekly, until = nov(23, 10))),
            )
        val takes = MedicineTakes(medicine, null, emptyMap())

        assertEquals(nov(23, 16), takes.take(nov(23, 8), nowAt(nov(23, 12)))?.missedAt)
        assertEquals(TakeState.DUE, takes.take(nov(23, 8), nowAt(nov(23, 15, 59)))?.state)
        assertEquals(TakeState.MISSED, takes.take(nov(23, 8), nowAt(nov(23, 16)))?.state)
    }

    @Test
    fun `after an end only takes up to it exist, and one answered after it`() {
        val answers = mapOf(nov(25, 16) to Answer.Taken(nov(25, 11)))
        val takes = MedicineTakes(Example.amoxicillin, nov(25, 12), answers)

        assertEquals(
            listOf(nov(23, 8), nov(23, 16), nov(24, 0), nov(24, 8), nov(24, 16), nov(25, 0), nov(25, 8), nov(25, 16)),
            takes.planned(),
        )
        assertEquals(8L, takes.count)
        assertEquals(nov(25, 16), takes.last)
        assertFalse(nov(26, 0) in takes)
    }

    @Test
    fun `a take Due when the treatment ends is Missed at once`() {
        val takes = MedicineTakes(Example.amoxicillin, nov(25, 12), emptyMap())

        assertEquals(nov(25, 12), takes.take(nov(25, 8), nowAt(nov(25, 12)))?.missedAt)
        assertEquals(TakeState.MISSED, takes.take(nov(25, 8), nowAt(nov(25, 12)))?.state)
        assertEquals(nov(24, 8), takes.take(nov(24, 0), nowAt(nov(25, 12)))?.missedAt)
    }

    @Test
    fun `a no-end medicine gets a count and a last take once its treatment ends`() {
        val noEnd = Example.amoxicillin.copy(schedule = everyEightHours.copy(duration = MedicineDuration.NoEnd))

        assertNull(MedicineTakes(noEnd, null, emptyMap()).count)
        assertNull(MedicineTakes(noEnd, null, emptyMap()).last)
        assertEquals(7L, MedicineTakes(noEnd, nov(25, 12), emptyMap()).count)
        assertEquals(nov(25, 8), MedicineTakes(noEnd, nov(25, 12), emptyMap()).last)
    }

    @Test
    fun `a treatment ended before its first take has no takes`() {
        val takes = MedicineTakes(Example.amoxicillin, nov(22, 12), emptyMap())

        assertEquals(0L, takes.count)
        assertNull(takes.first)
        assertNull(takes.last)
    }

    @Test
    fun `answers that belong to no take are ignored`() {
        val answers = mapOf(nov(23, 9) to Answer.Skipped, nov(24, 16) to Answer.Skipped, nov(23, 8) to Answer.Skipped)
        val takes = MedicineTakes(edited, null, answers)

        assertEquals(setOf(nov(23, 8)), takes.answers.keys)
    }

    // The queries, on spec §4.4's Amoxicillin.

    private fun amoxicillin(vararg answers: Pair<LocalDateTime, Answer>) = MedicineTakes(Example.amoxicillin, null, mapOf(*answers))

    private val taken0800 = nov(23, 8) to Answer.Taken(nov(23, 8))

    @Test
    fun `the next take is the Due one, or else the first unanswered take still to come`() {
        assertEquals(nov(23, 8), amoxicillin().nextTake(nowAt(nov(22, 12)))?.key?.plannedAt)
        assertEquals(TakeState.DUE, amoxicillin().nextTake(nowAt(nov(23, 10, 30)))?.state)
        assertEquals(nov(23, 8), amoxicillin().nextTake(nowAt(nov(23, 10, 30)))?.key?.plannedAt)

        val afterTaken = amoxicillin(taken0800).nextTake(nowAt(nov(23, 10, 30)))
        assertEquals(nov(23, 16), afterTaken?.key?.plannedAt)
        assertEquals(TakeState.PENDING, afterTaken?.state)

        val sixteenTaken = amoxicillin(taken0800, nov(23, 16) to Answer.Taken(nov(23, 16, 1)))
        assertEquals(nov(24, 0), sixteenTaken.nextTake(nowAt(nov(23, 16, 5)))?.key?.plannedAt)
    }

    @Test
    fun `there is no next take once the last one is answered or Missed`() {
        assertNull(amoxicillin().nextTake(nowAt(nov(30, 8))))
        assertNull(amoxicillin(nov(30, 0) to Answer.Skipped).nextTake(nowAt(nov(30, 0, 1))))
    }

    @Test
    fun `the next take can be marked early while it is Pending, but not the one after it`() {
        val takes = amoxicillin(taken0800)
        val now = nowAt(nov(23, 10, 30))

        assertTrue(takes.canMarkEarly(nov(23, 16), now))
        assertFalse(takes.canMarkEarly(nov(24, 0), now))
        assertFalse(takes.canMarkEarly(nov(23, 8), now))
        assertFalse(takes.canMarkEarly(nov(23, 9), now))
    }

    @Test
    fun `nothing can be marked early while a take is Due`() {
        assertFalse(amoxicillin().canMarkEarly(nov(23, 16), nowAt(nov(23, 10, 30))))
    }

    @Test
    fun `only one take can be answered ahead of its time`() {
        val markedEarly = amoxicillin(taken0800, nov(23, 16) to Answer.Taken(nov(23, 10, 30)))

        assertEquals(nov(24, 0), markedEarly.nextTake(nowAt(nov(23, 10, 31)))?.key?.plannedAt)
        assertFalse(markedEarly.canMarkEarly(nov(24, 0), nowAt(nov(23, 10, 31))))
        assertFalse(markedEarly.canMarkEarly(nov(24, 0), nowAt(nov(23, 15, 59))))
        assertTrue(markedEarly.canMarkEarly(nov(24, 0), nowAt(nov(23, 16))))
    }

    @Test
    fun `the last answered take is the one with the latest planned time, and progress counts answers`() {
        val takes = amoxicillin(taken0800, nov(23, 16) to Answer.Skipped, nov(24, 8) to Answer.Taken(nov(24, 8, 20)))

        assertEquals(nov(24, 8), takes.lastAnswered(nowAt(nov(24, 9)))?.key?.plannedAt)
        assertEquals(3, takes.answeredCount)
        assertNull(amoxicillin().lastAnswered(nowAt(nov(24, 9))))
        assertEquals(0, amoxicillin().answeredCount)
    }

    @Test
    fun `the next state change is when the Due take is Missed or the next one falls due`() {
        val now = nowAt(nov(23, 10, 30))

        assertEquals(nowAt(nov(23, 16)).toInstant(), amoxicillin(taken0800).nextStateChange(now))
        assertEquals(nowAt(nov(23, 16)).toInstant(), amoxicillin().nextStateChange(now))
        assertEquals(nowAt(nov(23, 8)).toInstant(), amoxicillin().nextStateChange(nowAt(nov(22, 12))))
    }

    @Test
    fun `the next state change skips a take answered early, and ends with the last take`() {
        val markedEarly = amoxicillin(taken0800, nov(23, 16) to Answer.Taken(nov(23, 10, 30)))

        assertEquals(nowAt(nov(24, 0)).toInstant(), markedEarly.nextStateChange(nowAt(nov(23, 10, 31))))
        assertEquals(nowAt(nov(30, 8)).toInstant(), amoxicillin().nextStateChange(nowAt(nov(30, 1))))
        assertNull(amoxicillin().nextStateChange(nowAt(nov(30, 8))))
    }

    @Test
    fun `a no-end medicine has a next take years ahead`() {
        val weekly = Example.amoxicillin.copy(schedule = schedule(nov(23, 8), 1, IntervalUnit.WEEKS, MedicineDuration.NoEnd))
        val takes = MedicineTakes(weekly, null, emptyMap())
        val later = nowAt(nov(23, 10).plusYears(10))
        val next = checkNotNull(takes.nextTake(later))

        assertEquals(TakeState.DUE, next.state)
        assertTrue(next.key.plannedAt <= later.toLocalDateTime() && next.missedAt > later.toLocalDateTime())
        assertNull(takes.count)
    }
}
