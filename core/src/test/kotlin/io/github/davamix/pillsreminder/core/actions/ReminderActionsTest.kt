package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.MADRID
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.reminders.ReminderState
import io.github.davamix.pillsreminder.core.reminders.TakeReminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime

/** The snooze on `PillsData` and on the repository (P5 SPEC §4.2, RM-3). P4's `ReminderStateTest` covers its rules. */
class ReminderActionsTest {
    private val k0800 = TakeKey(Example.amoxicillinId, nov(23, 8))
    private val k1600 = TakeKey(Example.amoxicillinId, nov(23, 16))

    private fun clockAt(local: LocalDateTime): Clock = Clock.fixed(local.atZone(MADRID).toInstant(), MADRID)

    @Test
    fun `a Due take's reminder is snoozed until 10 minutes after now, in whole seconds, and nothing else changes`() {
        val now = nowAt(nov(23, 8, 5)).withSecond(30).withNano(500_000_000)

        val snoozed = checkNotNull(Example.data.snooze(k0800, now))

        assertEquals(ReminderState(mapOf(k0800 to TakeReminder.Snoozed(Instant.parse("2026-11-23T07:15:30Z")))), snoozed.reminders)
        assertEquals(Example.data.treatments, snoozed.treatments)
        assertEquals(Example.data.answers, snoozed.answers)
    }

    @Test
    fun `a snooze is refused for a take that isn't Due, and for one already snoozed`() {
        val now = nowAt(nov(23, 8, 5))
        val taken = Example.data.copy(answers = mapOf(k0800 to Answer.Taken(nov(23, 8, 1))))
        val snoozed = checkNotNull(Example.data.snooze(k0800, now))

        assertNull(Example.data.snooze(k1600, now), "Pending")
        assertNull(taken.snooze(k0800, now), "Taken")
        assertNull(Example.data.snooze(k0800, nowAt(nov(23, 16, 1))), "Missed")
        assertNull(snoozed.snooze(k0800, nowAt(nov(23, 8, 9))), "already snoozed")
    }

    @Test
    fun `the repository helper snoozes inside update, reading the given clock, and returns false when refused`() =
        runTest {
            val repository = InMemoryTreatmentRepository(StoredData.Readable(Example.data))

            assertTrue(repository.snooze(k0800, clockAt(nov(23, 8, 5))))
            val record = TakeReminder.Snoozed(Instant.parse("2026-11-23T07:15:00Z"))
            assertEquals(ReminderState(mapOf(k0800 to record)), current(repository).reminders)

            assertFalse(repository.snooze(k0800, clockAt(nov(23, 8, 9))))
            assertFalse(repository.snooze(k1600, clockAt(nov(23, 8, 9))))
            assertEquals(ReminderState(mapOf(k0800 to record)), current(repository).reminders)
        }

    private suspend fun current(repository: InMemoryTreatmentRepository): PillsData = (repository.data.first() as StoredData.Readable).data
}
