package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.assertInvariants
import io.github.davamix.pillsreminder.core.at
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime

/** Spec §4.4, walked minute by minute, and the home screen's numbers on its first day. */
class ExampleWalkTest {
    private val start = nov(22, 0)
    private val end = at(12, 2, 0)

    private fun expectedState(
        plannedAt: LocalDateTime,
        now: LocalDateTime,
    ): TakeState =
        when {
            now < plannedAt -> TakeState.PENDING
            now < plannedAt.plusHours(8) -> TakeState.DUE
            else -> TakeState.MISSED
        }

    @Test
    fun `with no answers, every take is Pending, then Due for 8 hours, then Missed, minute by minute`() {
        val data = Example.data
        val medicines = listOf(Example.amoxicillinId, Example.atroventId).map { checkNotNull(data.takesOf(it)) }
        var previous: Map<TakeKey, TakeState>? = null
        var predicted: Instant? = null
        var now = start
        while (now < end) {
            val zoned = nowAt(now)
            val states = medicines.flatMap { it.takes(start, end, zoned).toList() }.associate { it.key to it.state }
            states.forEach { (key, state) -> assertEquals(expectedState(key.plannedAt, now), state, "${key.plannedAt} at $now") }
            if (previous != null) {
                val changed = states != previous
                val changesNow = predicted == zoned.toInstant()
                assertEquals(changed, changesNow, "the predicted change at $now")
            }
            assertEquals(expectedStatus(now), data.status(Example.treatmentId, zoned), "the status at $now")
            if (now.minute == 0) assertInvariants(data, zoned)
            previous = states
            predicted = medicines.mapNotNull { it.nextStateChange(zoned) }.minOrNull()
            now = now.plusMinutes(1)
        }
    }

    private fun expectedStatus(now: LocalDateTime): TreatmentStatus =
        when {
            now < nov(23, 8) -> TreatmentStatus.UPCOMING
            now < nov(30, 8) -> TreatmentStatus.ACTIVE
            else -> TreatmentStatus.FINISHED
        }

    @Test
    fun `on Monday at 1030, with both 0800 takes Taken, both next takes are at 1600, 1 of 21 and 1 of 15`() {
        val answers =
            mapOf(
                TakeKey(Example.amoxicillinId, nov(23, 8)) to Answer.Taken(nov(23, 8)),
                TakeKey(Example.atroventId, nov(23, 8)) to Answer.Taken(nov(23, 8)),
            )
        val data = Example.data.copy(answers = answers)
        val now = nowAt(nov(23, 10, 30))
        val amoxicillin = checkNotNull(data.takesOf(Example.amoxicillinId))
        val atrovent = checkNotNull(data.takesOf(Example.atroventId))

        assertEquals(nov(23, 16), amoxicillin.nextTake(now)?.key?.plannedAt)
        assertEquals(nov(23, 16), atrovent.nextTake(now)?.key?.plannedAt)
        assertEquals(1 to 21L, amoxicillin.answeredCount to amoxicillin.count)
        assertEquals(1 to 15L, atrovent.answeredCount to atrovent.count)
        assertInvariants(data, now)
    }

    @Test
    fun `on Monday at 1605, Amoxicillin's 1600 take is Due, 1 of 21, and Atrovent's next is Tuesday at 0000, 2 of 15`() {
        val answers =
            mapOf(
                TakeKey(Example.amoxicillinId, nov(23, 8)) to Answer.Taken(nov(23, 8)),
                TakeKey(Example.atroventId, nov(23, 8)) to Answer.Taken(nov(23, 8)),
                TakeKey(Example.atroventId, nov(23, 16)) to Answer.Taken(nov(23, 16, 1)),
            )
        val data = Example.data.copy(answers = answers)
        val now = nowAt(nov(23, 16, 5))
        val amoxicillin = checkNotNull(data.takesOf(Example.amoxicillinId))
        val atrovent = checkNotNull(data.takesOf(Example.atroventId))

        val due = checkNotNull(amoxicillin.nextTake(now))
        assertEquals(nov(23, 16) to TakeState.DUE, due.key.plannedAt to due.state)
        assertEquals(1, amoxicillin.answeredCount)
        assertEquals(nov(24, 0), atrovent.nextTake(now)?.key?.plannedAt)
        assertEquals(2, atrovent.answeredCount)
        assertTrue(atrovent.nextTake(now)?.state == TakeState.PENDING)
        assertInvariants(data, now)
    }
}
