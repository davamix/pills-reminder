package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.MADRID
import io.github.davamix.pillsreminder.core.at
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TakeStatesTest {
    private fun takesOf(
        medicine: Medicine,
        answers: Map<LocalDateTime, Answer> = emptyMap(),
    ) = MedicineTakes(medicine, null, answers)

    private fun MedicineTakes.stateAt(
        plannedAt: LocalDateTime,
        now: LocalDateTime,
    ): TakeState? = take(plannedAt, nowAt(now))?.state

    private fun medicine(
        firstTake: LocalDateTime,
        amount: Int,
        unit: IntervalUnit,
        duration: MedicineDuration,
    ) = Medicine(MedicineId("test"), "Amoxicillin", "1 capsule", "", schedule(firstTake, amount, unit, duration))

    @Test
    fun `a take is Pending before its time, Due from it, and Missed one interval later`() {
        val takes = takesOf(Example.amoxicillin)

        assertEquals(TakeState.PENDING, takes.stateAt(nov(23, 8), nov(23, 7, 59)))
        assertEquals(TakeState.DUE, takes.stateAt(nov(23, 8), nov(23, 8)))
        assertEquals(TakeState.DUE, takes.stateAt(nov(23, 8), nov(23, 15, 59)))
        assertEquals(TakeState.MISSED, takes.stateAt(nov(23, 8), nov(23, 16)))
        assertEquals(nov(23, 16), takes.take(nov(23, 8), nowAt(nov(23, 9)))?.missedAt)
    }

    @Test
    fun `an answered take is Taken or Skipped whatever the time`() {
        val takes = takesOf(Example.amoxicillin, mapOf(nov(23, 8) to Answer.Taken(nov(23, 8, 20)), nov(23, 16) to Answer.Skipped))

        for (now in listOf(nov(23, 7), nov(23, 12), nov(25, 12))) {
            assertEquals(TakeState.TAKEN, takes.stateAt(nov(23, 8), now))
            assertEquals(TakeState.SKIPPED, takes.stateAt(nov(23, 16), now))
        }
        assertEquals(nov(23, 8, 20), takes.take(nov(23, 8), nowAt(nov(23, 12)))?.takenAt)
        assertNull(takes.take(nov(23, 16), nowAt(nov(23, 12)))?.takenAt)
    }

    @Test
    fun `with a weekly interval a take becomes Missed a week later`() {
        val takes = takesOf(medicine(nov(23, 8), 1, IntervalUnit.WEEKS, MedicineDuration.NoEnd))

        assertEquals(nov(30, 8), takes.take(nov(23, 8), nowAt(nov(23, 9)))?.missedAt)
        assertEquals(TakeState.DUE, takes.stateAt(nov(23, 8), nov(30, 7, 59)))
        assertEquals(TakeState.MISSED, takes.stateAt(nov(23, 8), nov(30, 8)))
    }

    @Test
    fun `the last take becomes Missed one interval after it`() {
        val takes = takesOf(Example.amoxicillin)

        assertEquals(nov(30, 8), takes.take(nov(30, 0), nowAt(nov(30, 1)))?.missedAt)
        assertEquals(TakeState.DUE, takes.stateAt(nov(30, 0), nov(30, 7, 59)))
        assertEquals(TakeState.MISSED, takes.stateAt(nov(30, 0), nov(30, 8)))
    }

    @Test
    fun `a take stays Due through the repeated hour, though the clock goes back before its time`() {
        val takes = takesOf(medicine(at(10, 24, 2, 30), 1, IntervalUnit.DAYS, MedicineDuration.Days(3)))
        val secondPass = ZonedDateTime.ofInstant(at(10, 25, 1, 15).toInstant(ZoneOffset.UTC), MADRID)

        assertEquals(at(10, 25, 2, 15), secondPass.toLocalDateTime())
        assertEquals(TakeState.DUE, takes.take(at(10, 25, 2, 30), secondPass)?.state)
    }

    @Test
    fun `after a change of zone westwards, a Due take is Pending again and answers don't change`() {
        val takes = takesOf(Example.amoxicillin, mapOf(nov(23, 8) to Answer.Taken(nov(23, 8))))
        val madrid = nowAt(nov(23, 16, 5))
        val canary = madrid.withZoneSameInstant(ZoneId.of("Atlantic/Canary"))

        assertEquals(TakeState.DUE, takes.take(nov(23, 16), madrid)?.state)
        assertEquals(TakeState.PENDING, takes.take(nov(23, 16), canary)?.state)
        assertEquals(TakeState.TAKEN, takes.take(nov(23, 8), canary)?.state)
    }

    @Test
    fun `taking a take late or early doesn't move the later ones`() {
        val unanswered = takesOf(Example.amoxicillin).takes(nov(23, 0), at(12, 1, 0), nowAt(nov(23, 12))).map { it.key }
        val late = mapOf(nov(23, 8) to Answer.Taken(nov(23, 11, 45)), nov(23, 16) to Answer.Taken(nov(23, 14)))
        val answered = takesOf(Example.amoxicillin, late).takes(nov(23, 0), at(12, 1, 0), nowAt(nov(23, 14))).map { it.key }

        assertEquals(unanswered.toList(), answered.toList())
    }

    @Test
    fun `a first take two days in the past makes the earlier takes Missed and the latest Due`() {
        val takes = takesOf(medicine(nov(21, 8), 8, IntervalUnit.HOURS, MedicineDuration.Days(7)))
        val states = takes.takes(nov(21, 0), nov(23, 9), nowAt(nov(23, 10, 30))).map { it.state }.toList()

        assertEquals(List(6) { TakeState.MISSED } + TakeState.DUE, states)
    }

    @Test
    fun `there is no take at a time the schedule doesn't plan`() {
        val takes = takesOf(Example.amoxicillin)
        val now = nowAt(nov(23, 12))

        assertNull(takes.take(nov(23, 9), now))
        assertNull(takes.take(nov(30, 8), now))
        assertNull(takes.take(nov(23, 0), now))
    }

    @Test
    fun `the takes between two times come in order, from the first one at or after the start`() {
        val takes = takesOf(Example.amoxicillin).takes(nov(23, 16), nov(24, 16), nowAt(nov(23, 12)))

        assertEquals(listOf(nov(23, 16), nov(24, 0), nov(24, 8)), takes.map { it.key.plannedAt }.toList())
        assertEquals(TakeKey(Example.amoxicillinId, nov(23, 16)), takes.first().key)
    }
}
