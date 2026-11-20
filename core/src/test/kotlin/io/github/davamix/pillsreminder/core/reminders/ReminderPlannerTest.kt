package io.github.davamix.pillsreminder.core.reminders

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.MADRID
import io.github.davamix.pillsreminder.core.actions.clearAnswer
import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.endTreatment
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.actions.skip
import io.github.davamix.pillsreminder.core.at
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.Schedule
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.toDraft
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.nowAt
import io.github.davamix.pillsreminder.core.schedule
import io.github.davamix.pillsreminder.core.time.dueAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderPlannerTest {
    private val amox0800 = TakeKey(Example.amoxicillinId, nov(23, 8))
    private val atro0800 = TakeKey(Example.atroventId, nov(23, 8))
    private val amox1600 = TakeKey(Example.amoxicillinId, nov(23, 16))
    private val atro1600 = TakeKey(Example.atroventId, nov(23, 16))

    /** The §4.4 example with Amoxicillin only, so that one take's reminder can be followed on its own. */
    private val amoxOnly = PillsData(listOf(Example.treatment.copy(medicines = listOf(Example.amoxicillin))))

    private val london = ZoneId.of("Europe/London")
    private val athens = ZoneId.of("Europe/Athens")

    /** A treatment of its own, with one made-up medicine. */
    private fun treatment(
        id: String,
        schedule: Schedule,
    ) = Treatment(TreatmentId(id), "Treatment $id", "", listOf(Medicine(MedicineId(id), "Testamol", "1 tablet", "", schedule)))

    private fun plan(
        data: PillsData,
        state: ReminderState,
        now: ZonedDateTime,
    ): ReminderPlan = ReminderPlanner.plan(data, state, now).also { assertPlanProperties(data, it, now) }

    private fun plan(
        data: PillsData,
        state: ReminderState,
        local: LocalDateTime,
    ): ReminderPlan = plan(data, state, nowAt(local))

    private fun records(vararg records: Pair<TakeKey, TakeReminder>) = ReminderState(mapOf(*records))

    private fun alerted(
        count: Int,
        local: LocalDateTime,
    ) = TakeReminder.Alerted(count, instant(local))

    private fun snoozed(local: LocalDateTime) = TakeReminder.Snoozed(instant(local))

    // The table of SPEC §4.3.3, row by row.

    @Test
    fun `a Due take with no record alerts, and records its first alert`() {
        val plan = plan(Example.data, ReminderState(), nov(23, 8))

        assertEquals(listOf(Reminder(amox0800, alert = true), Reminder(atro0800, alert = true)), plan.reminders)
        assertEquals(records(amox0800 to alerted(1, nov(23, 8)), atro0800 to alerted(1, nov(23, 8))), plan.recorded)
        assertEquals(instant(nov(23, 8, 10)), plan.nextWakeUp)
    }

    @Test
    fun `a snoozed take stays out of the shade until its snooze ends`() {
        val before = records(amox0800 to snoozed(nov(23, 8, 55)))
        val plan = plan(amoxOnly, before, nov(23, 8, 45))

        assertEquals(emptyList<Reminder>(), plan.reminders)
        assertEquals(before, plan.recorded)
        assertEquals(instant(nov(23, 8, 55)), plan.nextWakeUp)
    }

    @Test
    fun `when its snooze has ended, the take alerts and a new round starts`() {
        val before = records(amox0800 to snoozed(nov(23, 8, 55)))

        val onTime = plan(amoxOnly, before, nov(23, 8, 55))
        val late = plan(amoxOnly, before, nov(23, 8, 57))

        assertEquals(listOf(Reminder(amox0800, alert = true)), onTime.reminders)
        assertEquals(records(amox0800 to alerted(1, nov(23, 8, 55))), onTime.recorded)
        assertEquals(instant(nov(23, 9, 5)), onTime.nextWakeUp)
        assertEquals(records(amox0800 to alerted(1, nov(23, 8, 57))), late.recorded)
        assertEquals(instant(nov(23, 9, 7)), late.nextWakeUp)
    }

    @Test
    fun `a repeat is due 10 minutes after the last alert, up to 4 alerts in a round`() {
        val second = plan(amoxOnly, records(amox0800 to alerted(1, nov(23, 8))), nov(23, 8, 10))
        val fourth = plan(amoxOnly, records(amox0800 to alerted(3, nov(23, 8, 20))), nov(23, 8, 30))

        assertEquals(listOf(amox0800), second.alerts)
        assertEquals(records(amox0800 to alerted(2, nov(23, 8, 10))), second.recorded)
        assertEquals(instant(nov(23, 8, 20)), second.nextWakeUp)
        assertEquals(listOf(amox0800), fourth.alerts)
        assertEquals(records(amox0800 to alerted(4, nov(23, 8, 30))), fourth.recorded)
        assertEquals(instant(nov(23, 16)), fourth.nextWakeUp, "the round is over: the next wake-up is the Missed time")
    }

    @Test
    fun `between alerts, and after the fourth, the reminder is shown silently`() {
        val between = records(amox0800 to alerted(2, nov(23, 8, 10)))
        val over = records(amox0800 to alerted(4, nov(23, 8, 30)))

        val inRound = plan(amoxOnly, between, nov(23, 8, 19))
        val afterRound = plan(amoxOnly, over, nov(23, 10))

        assertEquals(listOf(Reminder(amox0800, alert = false)), inRound.reminders)
        assertEquals(between, inRound.recorded)
        assertEquals(instant(nov(23, 8, 20)), inRound.nextWakeUp)
        assertEquals(listOf(Reminder(amox0800, alert = false)), afterRound.reminders)
        assertEquals(over, afterRound.recorded)
        assertEquals(instant(nov(23, 16)), afterRound.nextWakeUp)
    }

    @Test
    fun `takes that aren't Due get no record`() {
        val data = checkNotNull(Example.data.markTaken(amox0800, nowAt(nov(23, 8, 3))))
        val before =
            records(
                amox0800 to alerted(1, nov(23, 8)),
                atro0800 to alerted(1, nov(23, 8)),
                amox1600 to snoozed(nov(23, 8, 13)),
                TakeKey(Example.atroventId, nov(22, 16)) to alerted(4, nov(22, 16, 30)),
            )
        val plan = plan(data, before, nov(23, 8, 5))

        assertEquals(listOf(Reminder(atro0800, alert = false)), plan.reminders)
        assertEquals(records(atro0800 to alerted(1, nov(23, 8))), plan.recorded)
    }

    // The example of SPEC §4.3.9.

    @Test
    fun `the example timeline on Monday, step by step`() {
        var data = Example.data
        var state = ReminderState()

        fun reconcileAt(local: LocalDateTime): ReminderPlan = plan(data, state, local).also { state = it.recorded }

        reconcileAt(nov(23, 7)).let {
            assertEquals(emptyList<Reminder>(), it.reminders, "0700")
            assertEquals(instant(nov(23, 8)), it.nextWakeUp, "0700")
        }
        reconcileAt(nov(23, 8)).let {
            assertEquals(listOf(amox0800, atro0800), it.alerts, "0800")
            assertEquals(instant(nov(23, 8, 10)), it.nextWakeUp, "0800")
        }
        data = checkNotNull(data.markTaken(atro0800, nowAt(nov(23, 8, 2))))
        reconcileAt(nov(23, 8, 2)).let {
            assertEquals(listOf(Reminder(amox0800, alert = false)), it.reminders, "0802")
            assertEquals(setOf(atro0800), it.removals(setOf(amox0800, atro0800)), "0802")
            assertEquals(instant(nov(23, 8, 10)), it.nextWakeUp, "0802")
            assertEquals(records(amox0800 to alerted(1, nov(23, 8))), it.recorded, "0802")
        }
        reconcileAt(nov(23, 8, 10)).let {
            assertEquals(listOf(amox0800), it.alerts, "0810")
            assertEquals(records(amox0800 to alerted(2, nov(23, 8, 10))), it.recorded, "0810")
            assertEquals(instant(nov(23, 8, 20)), it.nextWakeUp, "0810")
        }
        reconcileAt(nov(23, 8, 13)).let {
            assertEquals(listOf(Reminder(amox0800, alert = false)), it.reminders, "0813")
            assertEquals(records(amox0800 to alerted(2, nov(23, 8, 10))), it.recorded, "0813")
            assertEquals(instant(nov(23, 8, 20)), it.nextWakeUp, "0813")
        }
        reconcileAt(nov(23, 8, 20)).let {
            assertEquals(listOf(amox0800), it.alerts, "0820")
            assertEquals(instant(nov(23, 8, 30)), it.nextWakeUp, "0820")
        }
        reconcileAt(nov(23, 8, 30)).let {
            assertEquals(listOf(amox0800), it.alerts, "0830")
            assertEquals(records(amox0800 to alerted(4, nov(23, 8, 30))), it.recorded, "0830")
            assertEquals(instant(nov(23, 16)), it.nextWakeUp, "0830")
        }
        state = checkNotNull(state.snooze(data, amox0800, nowAt(nov(23, 8, 45))))
        reconcileAt(nov(23, 8, 45)).let {
            assertEquals(emptyList<Reminder>(), it.reminders, "0845")
            assertEquals(setOf(amox0800), it.removals(setOf(amox0800)), "0845")
            assertEquals(records(amox0800 to snoozed(nov(23, 8, 55))), it.recorded, "0845")
            assertEquals(instant(nov(23, 8, 55)), it.nextWakeUp, "0845")
        }
        reconcileAt(nov(23, 8, 55)).let {
            assertEquals(listOf(amox0800), it.alerts, "0855")
            assertEquals(records(amox0800 to alerted(1, nov(23, 8, 55))), it.recorded, "0855")
            assertEquals(instant(nov(23, 9, 5)), it.nextWakeUp, "0855")
        }
        for ((local, next) in listOf(nov(23, 9, 5) to nov(23, 9, 15), nov(23, 9, 15) to nov(23, 9, 25), nov(23, 9, 25) to nov(23, 16))) {
            reconcileAt(local).let {
                assertEquals(listOf(amox0800), it.alerts, "$local")
                assertEquals(instant(next), it.nextWakeUp, "$local")
            }
        }
        assertEquals(records(amox0800 to alerted(4, nov(23, 9, 25))), state)
        reconcileAt(nov(23, 16)).let {
            assertEquals(listOf(amox1600, atro1600), it.alerts, "1600")
            assertEquals(setOf(amox0800), it.removals(setOf(amox0800)), "1600")
            assertEquals(records(amox1600 to alerted(1, nov(23, 16)), atro1600 to alerted(1, nov(23, 16))), it.recorded, "1600")
            assertEquals(instant(nov(23, 16, 10)), it.nextWakeUp, "1600")
        }
    }

    // Late alerts.

    @Test
    fun `a first alert 25 minutes late still repeats 3 times, 10 minutes after each alert`() {
        var state = ReminderState()
        val alerts = mutableListOf<LocalDateTime>()
        var next = nov(23, 8, 25)
        repeat(4) {
            val plan = plan(amoxOnly, state, next)
            if (plan.alerts == listOf(amox0800)) alerts += next
            state = plan.recorded
            next = checkNotNull(plan.nextWakeUp).atZone(MADRID).toLocalDateTime()
        }

        assertEquals(listOf(nov(23, 8, 25), nov(23, 8, 35), nov(23, 8, 45), nov(23, 8, 55)), alerts)
        assertEquals(nov(23, 16), next, "then the Missed time")
    }

    @Test
    fun `a reconcile 3 hours late alerts once, then repeats from there`() {
        val first = plan(amoxOnly, ReminderState(), nov(23, 11))
        val inRound = plan(amoxOnly, records(amox0800 to alerted(2, nov(23, 8, 10))), nov(23, 11))

        assertEquals(listOf(amox0800), first.alerts)
        assertEquals(records(amox0800 to alerted(1, nov(23, 11))), first.recorded)
        assertEquals(instant(nov(23, 11, 10)), first.nextWakeUp)
        assertEquals(listOf(amox0800), inRound.alerts)
        assertEquals(records(amox0800 to alerted(3, nov(23, 11))), inRound.recorded)
        assertEquals(instant(nov(23, 11, 10)), inRound.nextWakeUp)
    }

    @Test
    fun `a snooze that ends when the take becomes Missed doesn't alert`() {
        val plan = plan(amoxOnly, records(amox0800 to snoozed(nov(23, 16))), nov(23, 16))

        assertEquals(listOf(Reminder(amox1600, alert = true)), plan.reminders)
        assertEquals(setOf(amox1600), plan.recorded.takes.keys)
    }

    // The shade.

    @Test
    fun `takes of two treatments due at once each have their own reminder, and all alert`() {
        val other = treatment("other", schedule(nov(23, 8), 12, IntervalUnit.HOURS, MedicineDuration.Days(3)))
        val data = Example.data.copy(treatments = Example.data.treatments + other)
        val plan = plan(data, ReminderState(), nov(23, 8))

        assertEquals(listOf(amox0800, atro0800, TakeKey(MedicineId("other"), nov(23, 8))), plan.alerts)
    }

    @Test
    fun `with a first take in the past, only the latest take is in the shade, and it alerts once`() {
        val plan = plan(amoxOnly, ReminderState(), nov(25, 10))
        val again = plan(amoxOnly, plan.recorded, nov(25, 10, 5))

        assertEquals(listOf(Reminder(TakeKey(Example.amoxicillinId, nov(25, 8)), alert = true)), plan.reminders)
        assertEquals(emptyList<TakeKey>(), again.alerts)
    }

    @Test
    fun `reminders are in order of planned time, then of treatments and medicines in the data`() {
        val early = treatment("early", schedule(nov(23, 7), 12, IntervalUnit.HOURS, MedicineDuration.Days(3)))
        val data = Example.data.copy(treatments = Example.data.treatments + early)
        val plan = plan(data, ReminderState(), nov(23, 8, 5))

        assertEquals(listOf(TakeKey(MedicineId("early"), nov(23, 7)), amox0800, atro0800), plan.reminders.map { it.key })
    }

    @Test
    fun `the removals are the reminders in the shade that the plan doesn't hold, also those of takes that are gone`() {
        val plan = plan(Example.data, ReminderState(), nov(23, 8, 5))
        val gone = TakeKey(MedicineId("deleted"), nov(23, 8))
        val missed = TakeKey(Example.amoxicillinId, nov(23, 0))

        assertEquals(setOf(gone, missed), plan.removals(setOf(amox0800, gone, missed, atro0800)))
        assertEquals(emptySet<TakeKey>(), plan.removals(emptySet()))
    }

    // Removal.

    @Test
    fun `a take answered Taken or Skipped leaves the shade, and its record is dropped`() {
        val now = nowAt(nov(23, 8, 5))
        val shown = records(amox0800 to alerted(1, nov(23, 8)), atro0800 to alerted(1, nov(23, 8)))
        val data = checkNotNull(checkNotNull(Example.data.markTaken(amox0800, now)).skip(atro0800, now))
        val plan = plan(data, shown, now)

        assertEquals(emptyList<Reminder>(), plan.reminders)
        assertEquals(ReminderState(), plan.recorded)
        assertEquals(setOf(amox0800, atro0800), plan.removals(setOf(amox0800, atro0800)))
        assertEquals(instant(nov(23, 16)), plan.nextWakeUp)
    }

    @Test
    fun `a take left unanswered leaves the shade at its Missed time, which is a wake-up`() {
        val silent = plan(amoxOnly, records(amox0800 to alerted(4, nov(23, 8, 30))), nov(23, 15, 59))
        val missed = plan(amoxOnly, silent.recorded, nov(23, 16))

        assertEquals(instant(nov(23, 16)), silent.nextWakeUp)
        assertEquals(listOf(Reminder(amox1600, alert = true)), missed.reminders)
        assertEquals(setOf(amox0800), missed.removals(setOf(amox0800)))
    }

    @Test
    fun `after an edit, a Due take keeps its reminder without a new alert, and goes when the next take falls due`() {
        val now = nowAt(nov(23, 9))
        val draft = Example.treatment.toDraft().let { it.copy(medicines = it.medicines.take(1).map { m -> m.copy(intervalAmount = 2) }) }
        val edited = amoxOnly.saveTreatment(draft, now)
        val record = records(amox0800 to alerted(4, nov(23, 8, 30)))

        val plan = plan(edited, record, now)
        val next = plan(edited, plan.recorded, nov(23, 10))

        assertEquals(listOf(Reminder(amox0800, alert = false)), plan.reminders)
        assertEquals(record, plan.recorded)
        assertEquals(instant(nov(23, 10)), plan.nextWakeUp, "Missed at the next take, 1000, not at 1600")
        assertEquals(listOf(Reminder(TakeKey(Example.amoxicillinId, nov(23, 10)), alert = true)), next.reminders)
    }

    @Test
    fun `ending the treatment removes its reminders at once, and nothing of it wakes the app again`() {
        val now = nowAt(nov(23, 8, 15))
        val shown = records(amox0800 to alerted(2, nov(23, 8, 10)), atro0800 to alerted(2, nov(23, 8, 10)))
        val plan = plan(checkNotNull(Example.data.endTreatment(Example.treatmentId, now)), shown, now)

        assertEquals(emptyList<Reminder>(), plan.reminders)
        assertEquals(ReminderState(), plan.recorded)
        assertNull(plan.nextWakeUp)
    }

    @Test
    fun `deleting the treatment removes its reminders and records`() {
        val shown = records(amox0800 to alerted(2, nov(23, 8, 10)), atro0800 to alerted(2, nov(23, 8, 10)))
        val plan = plan(checkNotNull(Example.data.deleteTreatment(Example.treatmentId)), shown, nov(23, 8, 15))

        assertEquals(emptyList<Reminder>(), plan.reminders)
        assertEquals(ReminderState(), plan.recorded)
        assertEquals(setOf(amox0800, atro0800), plan.removals(setOf(amox0800, atro0800)))
        assertNull(plan.nextWakeUp)
    }

    @Test
    fun `removing a medicine removes its reminder, and the other stays`() {
        val now = nowAt(nov(23, 8, 15))
        val draft = Example.treatment.toDraft().let { it.copy(medicines = it.medicines.take(1)) }
        val shown = records(amox0800 to alerted(2, nov(23, 8, 10)), atro0800 to alerted(2, nov(23, 8, 10)))
        val plan = plan(Example.data.saveTreatment(draft, now), shown, now)

        assertEquals(listOf(Reminder(amox0800, alert = false)), plan.reminders)
        assertEquals(records(amox0800 to alerted(2, nov(23, 8, 10))), plan.recorded)
    }

    @Test
    fun `a take marked Taken early never alerts, and isn't a wake-up`() {
        val taken = checkNotNull(amoxOnly.markTaken(amox0800, nowAt(nov(23, 8, 5))))
        val early = checkNotNull(taken.markTaken(amox1600, nowAt(nov(23, 10, 30))))

        val now = plan(early, ReminderState(), nov(23, 10, 30))
        val atItsTime = plan(early, now.recorded, nov(23, 16))

        assertEquals(instant(nov(24, 0)), now.nextWakeUp)
        assertEquals(emptyList<Reminder>(), atItsTime.reminders)
    }

    @Test
    fun `an undone answer makes the take Due again, and its reminder alerts at once`() {
        val first = plan(amoxOnly, ReminderState(), nov(23, 8))
        val taken = checkNotNull(amoxOnly.markTaken(amox0800, nowAt(nov(23, 8, 2))))
        val answered = plan(taken, first.recorded, nov(23, 8, 2))
        val undone = checkNotNull(taken.clearAnswer(amox0800, nowAt(nov(23, 8, 5))))
        val plan = plan(undone, answered.recorded, nov(23, 8, 5))

        assertEquals(ReminderState(), answered.recorded)
        assertEquals(listOf(amox0800), plan.alerts)
        assertEquals(records(amox0800 to alerted(1, nov(23, 8, 5))), plan.recorded)
        assertEquals(instant(nov(23, 8, 15)), plan.nextWakeUp)
    }

    // The next wake-up.

    @Test
    fun `the next wake-up is the exact instant the first take falls due, to the millisecond`() {
        val justBefore = nowAt(nov(23, 7, 59)).withSecond(59).withNano(999_000_000)
        val plan = plan(Example.data, ReminderState(), justBefore)
        val wakeUp = checkNotNull(plan.nextWakeUp)

        assertEquals(nov(23, 8).dueAt(MADRID), wakeUp)
        assertEquals(listOf(amox0800, atro0800), plan(Example.data, plan.recorded, wakeUp.atZone(MADRID)).alerts)
    }

    @Test
    fun `with no treatment there is nothing to show and nothing to wake for`() {
        val plan = plan(PillsData(), records(amox0800 to alerted(1, nov(23, 8))), nov(23, 8, 5))

        assertEquals(emptyList<Reminder>(), plan.reminders)
        assertEquals(ReminderState(), plan.recorded)
        assertNull(plan.nextWakeUp)
    }

    @Test
    fun `once every take is answered or Missed there is nothing to wake for`() {
        val last = plan(Example.data, ReminderState(), nov(30, 7, 59))
        val after = plan(Example.data, last.recorded, nov(30, 8))

        assertEquals(listOf(TakeKey(Example.amoxicillinId, nov(30, 0))), last.alerts)
        assertEquals(instant(nov(30, 8)), last.nextWakeUp)
        assertEquals(emptyList<Reminder>(), after.reminders)
        assertNull(after.nextWakeUp)
    }

    // Time changes.

    @Test
    fun `a take planned in the 29 March gap alerts at 0330`() {
        val daily = treatment("daily", schedule(at(3, 28, 2, 30), 1, IntervalUnit.DAYS, MedicineDuration.Days(5)))
        val dayBefore = TakeKey(MedicineId("daily"), at(3, 28, 2, 30))
        val data = PillsData(listOf(daily), mapOf(dayBefore to Answer.Taken(at(3, 28, 2, 30))))
        val key = TakeKey(MedicineId("daily"), at(3, 29, 2, 30))

        val before = plan(data, ReminderState(), at(3, 29, 1, 59))
        val atIt = plan(data, before.recorded, checkNotNull(before.nextWakeUp).atZone(MADRID))

        assertEquals(nowAt(at(3, 29, 3, 30)).toInstant(), before.nextWakeUp)
        assertEquals(listOf(key), atIt.alerts)
    }

    @Test
    fun `repeats are 10 real minutes apart through the repeated hour of 25 October`() {
        val data = PillsData(listOf(treatment("daily", schedule(at(10, 24, 2, 30), 1, IntervalUnit.DAYS, MedicineDuration.Days(5)))))
        var now = nowAt(at(10, 25, 2, 30))
        var state = ReminderState()
        val alerts = mutableListOf<ZonedDateTime>()
        repeat(4) {
            val plan = plan(data, state, now)
            if (plan.alerts.isNotEmpty()) alerts += now
            state = plan.recorded
            now = checkNotNull(plan.nextWakeUp).atZone(MADRID)
        }

        val expected = (0L..3L).map { nowAt(at(10, 25, 2, 30)).plusMinutes(10 * it) }
        assertEquals(expected.map { it.toInstant() }, alerts.map { it.toInstant() })
        assertEquals(at(10, 25, 2, 0), alerts.last().toLocalDateTime(), "the fourth alert is at 0200, after the clock went back")
        assertEquals(ZoneOffset.ofHours(1), alerts.last().offset)
    }

    @Test
    fun `moving west makes a Due take Pending again, and it alerts again when it falls due on the new clock`() {
        val now = nowAt(nov(23, 8, 15)).withZoneSameInstant(london)
        val away = plan(amoxOnly, records(amox0800 to alerted(2, nov(23, 8, 10))), now)
        val due = plan(amoxOnly, away.recorded, checkNotNull(away.nextWakeUp).atZone(london))

        assertEquals(emptyList<Reminder>(), away.reminders)
        assertEquals(ReminderState(), away.recorded)
        assertEquals(nov(23, 8).dueAt(london), away.nextWakeUp)
        assertEquals(listOf(amox0800), due.alerts)
        assertEquals(TakeReminder.Alerted(1, nov(23, 8).dueAt(london)), due.recorded.takes[amox0800])
    }

    @Test
    fun `moving east keeps the reminder, its repeats and its snooze on the real clock`() {
        val now = nowAt(nov(23, 8, 15)).withZoneSameInstant(athens)
        val inRound = plan(amoxOnly, records(amox0800 to alerted(2, nov(23, 8, 10))), now)
        val inSnooze = plan(amoxOnly, records(amox0800 to snoozed(nov(23, 8, 25))), now)

        assertEquals(listOf(Reminder(amox0800, alert = false)), inRound.reminders)
        assertEquals(instant(nov(23, 8, 20)), inRound.nextWakeUp)
        assertEquals(emptyList<Reminder>(), inSnooze.reminders)
        assertEquals(instant(nov(23, 8, 25)), inSnooze.nextWakeUp)
    }

    @Test
    fun `with the clock set back an hour, no snooze or repeat waits more than 10 minutes`() {
        val now = nowAt(nov(23, 8, 30))
        val snoozeCase = plan(amoxOnly, records(amox0800 to snoozed(nov(23, 9, 40))), now)
        val repeatCase = plan(amoxOnly, records(amox0800 to alerted(2, nov(23, 9, 30))), now)

        assertEquals(records(amox0800 to snoozed(nov(23, 8, 40))), snoozeCase.recorded)
        assertEquals(instant(nov(23, 8, 40)), snoozeCase.nextWakeUp)
        assertEquals(listOf(Reminder(amox0800, alert = false)), repeatCase.reminders)
        assertEquals(records(amox0800 to alerted(2, nov(23, 8, 30))), repeatCase.recorded)
        assertEquals(instant(nov(23, 8, 40)), repeatCase.nextWakeUp)
    }

    @Test
    fun `with the clock set forward, an ended snooze and a due repeat alert once each`() {
        val snoozeCase = plan(amoxOnly, records(amox0800 to snoozed(nov(23, 8, 55))), nov(23, 10))
        val repeatCase = plan(amoxOnly, records(amox0800 to alerted(2, nov(23, 8, 10))), nov(23, 10))

        assertEquals(records(amox0800 to alerted(1, nov(23, 10))), snoozeCase.recorded)
        assertEquals(records(amox0800 to alerted(3, nov(23, 10))), repeatCase.recorded)
        assertEquals(listOf(amox0800), repeatCase.alerts)
        assertEquals(instant(nov(23, 10, 10)), repeatCase.nextWakeUp)
    }
}
