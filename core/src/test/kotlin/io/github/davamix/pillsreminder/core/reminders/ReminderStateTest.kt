package io.github.davamix.pillsreminder.core.reminders

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDateTime

class ReminderStateTest {
    private val amox0800 = TakeKey(Example.amoxicillinId, nov(23, 8))
    private val atro0800 = TakeKey(Example.atroventId, nov(23, 8))

    private fun instant(local: LocalDateTime): Instant = nowAt(local).toInstant()

    @Test
    fun `a Due take with no record is snoozed until 10 minutes after now`() {
        val snoozed = ReminderState().snooze(Example.data, amox0800, nowAt(nov(23, 8, 2)))

        assertEquals(ReminderState(mapOf(amox0800 to TakeReminder.Snoozed(instant(nov(23, 8, 12))))), snoozed)
    }

    @Test
    fun `the snooze ends 10 minutes after now in whole seconds`() {
        val now = nowAt(nov(23, 8, 2)).withSecond(30).withNano(250_000_000)
        val snoozed = checkNotNull(ReminderState().snooze(Example.data, amox0800, now))

        assertEquals(TakeReminder.Snoozed(instant(nov(23, 8, 12)).plusSeconds(30)), snoozed.takes[amox0800])
    }

    @Test
    fun `a silent reminder that has finished its round can be snoozed`() {
        val state = ReminderState(mapOf(amox0800 to TakeReminder.Alerted(4, instant(nov(23, 8, 30)))))
        val snoozed = state.snooze(Example.data, amox0800, nowAt(nov(23, 9)))

        assertEquals(TakeReminder.Snoozed(instant(nov(23, 9, 10))), snoozed?.takes?.get(amox0800))
    }

    @Test
    fun `a reminder in its round can be snoozed`() {
        val state = ReminderState(mapOf(amox0800 to TakeReminder.Alerted(2, instant(nov(23, 8, 10)))))
        val snoozed = state.snooze(Example.data, amox0800, nowAt(nov(23, 8, 15)))

        assertEquals(TakeReminder.Snoozed(instant(nov(23, 8, 25))), snoozed?.takes?.get(amox0800))
    }

    @Test
    fun `a snooze that has ended but isn't reconciled yet can be snoozed again`() {
        val state = ReminderState(mapOf(amox0800 to TakeReminder.Snoozed(instant(nov(23, 8, 12)))))

        val atTheEnd = state.snooze(Example.data, amox0800, nowAt(nov(23, 8, 12)))
        val after = state.snooze(Example.data, amox0800, nowAt(nov(23, 8, 13)))

        assertEquals(TakeReminder.Snoozed(instant(nov(23, 8, 22))), atTheEnd?.takes?.get(amox0800))
        assertEquals(TakeReminder.Snoozed(instant(nov(23, 8, 23))), after?.takes?.get(amox0800))
    }

    @Test
    fun `a take already snoozed can't be snoozed again, so a second tap can't stretch it`() {
        val state = ReminderState(mapOf(amox0800 to TakeReminder.Snoozed(instant(nov(23, 8, 12)))))

        assertNull(state.snooze(Example.data, amox0800, nowAt(nov(23, 8, 2)).plusSeconds(1)))
        assertNull(state.snooze(Example.data, amox0800, nowAt(nov(23, 8, 11)).withSecond(59)))
    }

    @Test
    fun `a Pending take can't be snoozed`() {
        assertNull(ReminderState().snooze(Example.data, amox0800, nowAt(nov(23, 7, 59))))
    }

    @Test
    fun `answered takes can't be snoozed`() {
        val taken = Example.data.copy(answers = mapOf(amox0800 to Answer.Taken(nov(23, 8, 1))))
        val skipped = Example.data.copy(answers = mapOf(amox0800 to Answer.Skipped))
        val now = nowAt(nov(23, 8, 2))

        assertNull(ReminderState().snooze(taken, amox0800, now))
        assertNull(ReminderState().snooze(skipped, amox0800, now))
    }

    @Test
    fun `a Missed take can't be snoozed`() {
        assertNull(ReminderState().snooze(Example.data, amox0800, nowAt(nov(23, 16))))
    }

    @Test
    fun `a take that doesn't exist can't be snoozed`() {
        val now = nowAt(nov(23, 8, 2))

        assertNull(ReminderState().snooze(Example.data, TakeKey(MedicineId("unknown"), nov(23, 8)), now))
        assertNull(ReminderState().snooze(Example.data, TakeKey(Example.amoxicillinId, nov(23, 7)), now), "no take at 0700")
    }

    @Test
    fun `a snooze leaves the other records as they are`() {
        val other = TakeReminder.Alerted(1, instant(nov(23, 8)))
        val state = ReminderState(mapOf(atro0800 to other))
        val snoozed = checkNotNull(state.snooze(Example.data, amox0800, nowAt(nov(23, 8, 2))))

        assertEquals(setOf(amox0800, atro0800), snoozed.takes.keys)
        assertEquals(other, snoozed.takes[atro0800])
    }

    @Test
    fun `a round has 1 to 4 alerts, and the message names only the number`() {
        val at = instant(nov(23, 8))

        assertEquals("A round has 1 to 4 alerts, not 0", assertThrows<IllegalArgumentException> { TakeReminder.Alerted(0, at) }.message)
        assertEquals("A round has 1 to 4 alerts, not 5", assertThrows<IllegalArgumentException> { TakeReminder.Alerted(5, at) }.message)
        TakeReminder.Alerted(1, at)
        TakeReminder.Alerted(4, at)
    }

    @Test
    fun `a record's times have whole seconds`() {
        val at = instant(nov(23, 8)).plusMillis(1)

        assertThrows<IllegalArgumentException> { TakeReminder.Snoozed(at) }
        assertThrows<IllegalArgumentException> { TakeReminder.Alerted(1, at) }
    }

    @Test
    fun `a snooze lasts 10 minutes and a round has 4 alerts, 10 minutes apart`() {
        assertEquals(10, ReminderPlanner.SNOOZE.toMinutes())
        assertEquals(10, ReminderPlanner.REPEAT_AFTER.toMinutes())
        assertEquals(4, ReminderPlanner.ALERTS_PER_ROUND)
    }
}
