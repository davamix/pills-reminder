package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.assertInvariants
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.take
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

class AnswersTest {
    private fun amox(plannedAt: LocalDateTime) = TakeKey(Example.amoxicillinId, plannedAt)

    private val k0800 = amox(nov(23, 8))
    private val k1600 = amox(nov(23, 16))
    private val k0000 = amox(nov(24, 0))

    private fun with(vararg answers: Pair<TakeKey, Answer>) = Example.data.copy(answers = mapOf(*answers))

    private val taken0800 = with(k0800 to Answer.Taken(nov(23, 8)))

    /** The change was allowed: checks the invariants, and returns the new data. */
    private fun PillsData?.allowed(now: ZonedDateTime): PillsData {
        val data = checkNotNull(this) { "the change was refused" }
        assertInvariants(data, now)
        return data
    }

    @Test
    fun `a Due take can be marked Taken, at now in whole minutes`() {
        val now = nowAt(nov(23, 8, 20)).withSecond(45)
        val data = Example.data.markTaken(k0800, now).allowed(now)

        assertEquals(Answer.Taken(nov(23, 8, 20)), data.answers[k0800])
    }

    @Test
    fun `the next take can be marked Taken early, and only that one`() {
        val now = nowAt(nov(23, 10, 30))
        val early = taken0800.markTaken(k1600, now).allowed(now)

        assertEquals(Answer.Taken(nov(23, 10, 30)), early.answers[k1600])
        assertNull(taken0800.markTaken(k0000, now))
        assertNull(early.markTaken(k0000, nowAt(nov(23, 10, 31))), "a second take ahead")
    }

    @Test
    fun `a Pending take can't be marked early while one is Due`() {
        assertNull(Example.data.markTaken(k1600, nowAt(nov(23, 10, 30))))
    }

    @Test
    fun `a Missed take can't be answered`() {
        val now = nowAt(nov(23, 16))

        assertEquals(TakeState.MISSED, Example.data.take(k0800, now)?.state)
        assertNull(Example.data.markTaken(k0800, now))
        assertNull(Example.data.skip(k0800, now))
        assertNull(Example.data.clearAnswer(k0800, now))
        assertNull(Example.data.setTakenTime(k0800, nov(23, 8), now))
    }

    @Test
    fun `a Due take can be skipped, and a Pending one can't`() {
        val now = nowAt(nov(23, 9))

        assertEquals(
            Answer.Skipped,
            Example.data
                .skip(k0800, now)
                .allowed(now)
                .answers[k0800],
        )
        assertNull(taken0800.skip(k1600, now))
    }

    @Test
    fun `the last answered take can be changed from Taken to Skipped and back`() {
        val now = nowAt(nov(23, 12))
        val skipped = taken0800.skip(k0800, now).allowed(now)
        val takenAgain = skipped.markTaken(k0800, now).allowed(now)

        assertEquals(Answer.Skipped, skipped.answers[k0800])
        assertEquals(Answer.Taken(nov(23, 12)), takenAgain.answers[k0800])
        assertNull(taken0800.markTaken(k0800, now), "already Taken")
    }

    @Test
    fun `older answered takes can't be changed`() {
        val data = with(k0800 to Answer.Taken(nov(23, 8)), k1600 to Answer.Skipped)
        val now = nowAt(nov(23, 17))

        assertNull(data.skip(k0800, now))
        assertNull(data.clearAnswer(k0800, now))
        assertNull(data.setTakenTime(k0800, nov(23, 8, 20), now))
        assertEquals(Answer.Taken(nov(23, 17)), data.markTaken(k1600, now).allowed(now).answers[k1600])
    }

    @Test
    fun `clearing the last answer makes the take Due or Missed again, by its time`() {
        val early = nowAt(nov(23, 10))
        val late = nowAt(nov(23, 17))

        assertEquals(
            TakeState.DUE,
            taken0800
                .clearAnswer(k0800, early)
                .allowed(early)
                .take(k0800, early)
                ?.state,
        )
        assertEquals(
            TakeState.MISSED,
            taken0800
                .clearAnswer(k0800, late)
                .allowed(late)
                .take(k0800, late)
                ?.state,
        )
        assertNull(taken0800.clearAnswer(k1600, early), "an unanswered take")
    }

    @Test
    fun `once the last answer is cleared, the one before it becomes the last`() {
        val data = with(k0800 to Answer.Taken(nov(23, 8)), k1600 to Answer.Taken(nov(23, 16)))
        val now = nowAt(nov(23, 17))
        val cleared = data.clearAnswer(k1600, now).allowed(now)

        assertEquals(Answer.Skipped, cleared.skip(k0800, now).allowed(now).answers[k0800])
    }

    @Test
    fun `the time taken can be corrected, to no later than now`() {
        val data = with(k0800 to Answer.Taken(nov(23, 8, 30)))
        val now = nowAt(nov(23, 9))

        assertEquals(Answer.Taken(nov(23, 8, 20)), data.setTakenTime(k0800, nov(23, 8, 20).withSecond(5), now).allowed(now).answers[k0800])
        assertEquals(Answer.Taken(nov(23, 9)), data.setTakenTime(k0800, nov(23, 9), now).allowed(now).answers[k0800])
        assertNull(data.setTakenTime(k0800, nov(23, 9, 1), now))
        assertNull(with(k0800 to Answer.Skipped).setTakenTime(k0800, nov(23, 8, 20), now), "a Skipped take")
    }

    @Test
    fun `a take that doesn't exist can't be answered`() {
        val now = nowAt(nov(23, 9))

        assertNull(Example.data.markTaken(TakeKey(MedicineId("unknown"), nov(23, 8)), now))
        assertNull(Example.data.markTaken(amox(nov(23, 9)), now))
        assertNull(Example.data.skip(amox(nov(30, 8)), now))
    }
}
