package io.github.davamix.pillsreminder.core.reminders

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.MADRID
import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.endTreatment
import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.at
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDraft
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentDraft
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.toDraft
import io.github.davamix.pillsreminder.core.nov
import io.github.davamix.pillsreminder.core.schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/** Simulated walks (SPEC §6): the planner over days and weeks, with reconciles at random moments and late alarms. */
class ReminderWalkTest {
    private val seeds = 1..5

    /** The §4.4 example, from the day before its first take to two days after its last. */
    private val exampleFrom = instant(nov(22, 0))
    private val exampleUntil = instant(at(12, 2, 0))

    /** A weekly medicine with no end, from Mon 12 Oct 2026 08:00, across the change of 25 October. */
    private val weeklyId = TreatmentId("weekly")
    private val weeklyMedicine =
        Medicine(MedicineId("weekly"), "Testamol", "1 tablet", "", schedule(at(10, 12, 8), 1, IntervalUnit.WEEKS, MedicineDuration.NoEnd))
    private val weekly = PillsData(listOf(Treatment(weeklyId, "Weekly example", "", listOf(weeklyMedicine))))
    private val weeklyFrom = instant(at(10, 12, 0))
    private val weeklyUntil = instant(at(11, 30, 12))

    /**
     * Each take's responses, the same for the phone and the oracle: Taken or Skipped some minutes after the reminder
     * appears, snoozed once or twice first, or left to be Missed. The 13 seconds keep responses off the minutes at which
     * alerts and Missed times fall, so no two events tie.
     */
    private fun responses(seed: Int): (TakeKey) -> List<Response> =
        { key ->
            val random = Random(seed * 1_000_003 + key.hashCode())

            fun after() = Duration.ofMinutes(random.nextLong(0, 45)).plusSeconds(13)

            fun response(kind: Response.Kind) = Response(after(), kind)
            when (random.nextInt(10)) {
                0, 1, 2 -> listOf(response(Response.Kind.TAKEN))
                3 -> listOf(response(Response.Kind.SKIP))
                4, 5 -> listOf(response(Response.Kind.SNOOZE), response(Response.Kind.TAKEN))
                6 -> listOf(response(Response.Kind.SNOOZE), response(Response.Kind.SNOOZE), response(Response.Kind.SKIP))
                else -> emptyList()
            }
        }

    private fun moments(
        seed: Int,
        count: Int,
        from: Instant,
        until: Instant,
    ): List<Instant> {
        val random = Random(seed)
        val span = Duration.between(from, until).toMillis()
        return List(count) { from.plusMillis(random.nextLong(span)) }
    }

    private fun assertMatchesOracle(
        phone: FakePhone,
        takes: List<OracleTake>,
        responses: (TakeKey) -> List<Response>,
        until: Instant,
        context: String,
    ) {
        val expected = takes.map { it.expected(responses(it.key), until) }
        assertSameLog(expected.flatMap { it.first }, phone.alerts, "$context: alerts")
        assertSameLog(expected.flatMap { it.second }, phone.removals, "$context: removals")
    }

    /** The walk's scripts include every kind of response, and takes left to be Missed. */
    private fun assertScriptsVaried(
        takes: List<OracleTake>,
        responses: (TakeKey) -> List<Response>,
        context: String,
    ) {
        val scripts = takes.map { responses(it.key) }
        for (kind in Response.Kind.entries) assertTrue(scripts.any { s -> s.any { it.kind == kind } }, "$context: some take gets $kind")
        assertTrue(scripts.any { it.isEmpty() }, "$context: some take is left to be Missed")
        assertTrue(scripts.any { s -> s.count { it.kind == Response.Kind.SNOOZE } == 2 }, "$context: some take is snoozed twice")
    }

    @Test
    fun `the example over ten days, with answers, snoozes, takes left Missed and reconciles at random moments, matches the oracle`() {
        for (seed in seeds) {
            val context = "example, seed $seed"
            val responses = responses(seed)
            val phone = FakePhone(Example.data, context = context)
            phone.walk(exampleFrom, exampleUntil, moments(seed, 300, exampleFrom, exampleUntil), responses)

            val takes = Example.treatment.medicines.flatMap { it.oracleTakes(MADRID, exampleUntil) }
            assertEquals(36, takes.size, "$context: 21 and 15 takes")
            assertScriptsVaried(takes, responses, context)
            assertMatchesOracle(phone, takes, responses, exampleUntil, context)
            assertNull(phone.alarm, "$context: nothing left to wake for")
        }
    }

    @Test
    fun `a weekly medicine with no end over seven weeks, across 25 October, matches the oracle`() {
        for (seed in seeds) {
            val context = "weekly, seed $seed"
            val responses = responses(seed)
            val phone = FakePhone(weekly, context = context)
            phone.walk(weeklyFrom, weeklyUntil, moments(seed, 100, weeklyFrom, weeklyUntil), responses)

            val takes = weeklyMedicine.oracleTakes(MADRID, weeklyUntil)
            assertEquals(8, takes.size, "$context: 12 Oct to 30 Nov")
            assertEquals(Duration.ofHours(169), Duration.between(takes[1].due, takes[2].due), "$context: the week of 25 October")
            assertMatchesOracle(phone, takes, responses, weeklyUntil, context)
            assertEquals(instant(at(12, 7, 8)), phone.alarm, "$context: the next take")
        }
    }

    @Test
    fun `the weekly medicine ended mid-walk while its reminder is in the shade matches the oracle, and nothing wakes the app after`() {
        val endedAt = instant(at(11, 4, 12)).plusSeconds(13)
        val inShade = TakeKey(weeklyMedicine.id, at(11, 2, 8))
        for (seed in seeds) {
            val context = "weekly ended, seed $seed"
            val responses: (TakeKey) -> List<Response> = { if (it == inShade) emptyList() else responses(seed)(it) }
            val end = Event(endedAt) { change(it) { data, now -> checkNotNull(data.endTreatment(weeklyId, now)) } }
            val phone = FakePhone(weekly, context = context)
            phone.walk(weeklyFrom, weeklyUntil, moments(seed, 100, weeklyFrom, weeklyUntil), responses, listOf(end))

            val takes = weeklyMedicine.oracleTakes(MADRID, weeklyUntil, endedAt)
            assertEquals(4, takes.size, "$context: up to Mon 2 Nov")
            assertMatchesOracle(phone, takes, responses, weeklyUntil, context)
            assertTrue(Logged(endedAt, inShade) in phone.removals, "$context: the reminder in the shade goes when the treatment ends")
            assertNull(phone.alarm, "$context: nothing left to wake for")
        }
    }

    @Test
    fun `reconciled every minute with no alarm, the example gives the same alerts and removals as when the alarm drives it`() {
        val byTheMinute = FakePhone(Example.data, context = "every minute")
        var t = exampleFrom
        while (t < exampleUntil) {
            byTheMinute.reconcile(t)
            t += Duration.ofMinutes(1)
        }
        val byAlarm = FakePhone(Example.data, context = "by the alarm")
        byAlarm.walk(exampleFrom, exampleUntil)

        val takes = Example.treatment.medicines.flatMap { it.oracleTakes(MADRID, exampleUntil) }
        assertMatchesOracle(byAlarm, takes, { emptyList() }, exampleUntil, "by the alarm")
        assertSameLog(byAlarm.alerts, byTheMinute.alerts, "every minute: alerts")
        assertSameLog(byAlarm.removals, byTheMinute.removals, "every minute: removals")
        assertEquals(36 * 4, byAlarm.alerts.size, "every take, left unanswered, alerts 4 times")
    }

    @Test
    fun `with alarms up to 15 minutes late, each take alerts at the first reconcile once due, then 3 more times 10 minutes apart`() {
        for (seed in seeds) {
            for ((name, data, range) in listOf(
                Triple("example", Example.data, exampleFrom to exampleUntil),
                Triple("weekly", weekly, weeklyFrom to weeklyUntil),
            )) {
                val context = "$name with late alarms, seed $seed"
                val (from, until) = range
                val delays = Random(seed)
                val phone = FakePhone(data, context = context)
                phone.walk(
                    from,
                    until,
                    moments(seed, 100, from, until),
                    alarmDelay = { Duration.ofMillis(delays.nextLong(0, 15 * 60_000L + 1)) },
                )

                val takes = data.treatments.flatMap { it.medicines }.flatMap { it.oracleTakes(MADRID, until) }
                val expected = takes.map { it.expectedAt(phone.reconciles) }
                assertSameLog(expected.flatMap { it.first }, phone.alerts, "$context: alerts")
                assertSameLog(expected.flatMap { it.second }, phone.removals, "$context: removals")
                val counts = takes.map { take -> phone.alerts.count { it.key == take.key } }
                assertTrue(counts.all { it == 4 }, "$context: every round has 4 alerts, with 8 hours or a week to play them: $counts")
            }
        }
    }

    // Events while a reminder is in the shade, on the example's first morning.

    private val amox0800 = TakeKey(Example.amoxicillinId, nov(23, 8))
    private val atro0800 = TakeKey(Example.atroventId, nov(23, 8))
    private val morning = instant(nov(23, 7))
    private val eventAt = instant(nov(23, 8, 15)).plusSeconds(13)

    private fun FakePhone.alertsOf(key: TakeKey) = alerts.filter { it.key == key }.map { it.at }

    private fun FakePhone.removalsOf(key: TakeKey) = removals.filter { it.key == key }.map { it.at }

    private fun instants(vararg locals: LocalDateTime) = locals.map { instant(it) }

    /** Walks the example's first morning, until [until], with [event] at 08:15:13 and reconciles at random moments. */
    private fun morningWith(
        until: Instant,
        event: FakePhone.(Instant) -> Unit,
        context: String,
    ): FakePhone =
        FakePhone(Example.data, context = context).apply {
            walk(morning, until, moments(1, 30, morning, until), events = listOf(Event(eventAt, event)))
        }

    /** The example's draft, with its medicines changed by [change]. */
    private fun exampleWith(change: List<MedicineDraft>.() -> List<MedicineDraft>): TreatmentDraft =
        Example.treatment.toDraft().let { it.copy(medicines = it.medicines.change()) }

    /** An event that saves [draft]. */
    private fun saving(draft: TreatmentDraft): FakePhone.(Instant) -> Unit =
        { at -> change(at) { data, now -> data.saveTreatment(draft, now) } }

    @Test
    fun `an edit keeps the reminder without a new alert, and it goes at its sooner Missed time`() {
        val everyTwoHours = exampleWith { map { if (it.id == Example.amoxicillinId) it.copy(intervalAmount = 2) else it } }
        val phone = morningWith(instant(nov(23, 12)), saving(everyTwoHours), "edit")
        val amox1000 = TakeKey(Example.amoxicillinId, nov(23, 10))

        assertEquals(instants(nov(23, 8), nov(23, 8, 10), nov(23, 8, 20), nov(23, 8, 30)), phone.alertsOf(amox0800))
        assertEquals(instants(nov(23, 10)), phone.removalsOf(amox0800))
        assertEquals(instants(nov(23, 10), nov(23, 10, 10), nov(23, 10, 20), nov(23, 10, 30)), phone.alertsOf(amox1000))
        assertEquals(instants(nov(23, 8), nov(23, 8, 10), nov(23, 8, 20), nov(23, 8, 30)), phone.alertsOf(atro0800))
    }

    @Test
    fun `deleting the treatment removes both reminders at once, and nothing alerts or wakes the app after`() {
        val phone =
            morningWith(
                instant(nov(23, 12)),
                { at -> change(at) { data, _ -> checkNotNull(data.deleteTreatment(Example.treatmentId)) } },
                "delete",
            )

        assertEquals(listOf(eventAt), phone.removalsOf(amox0800))
        assertEquals(listOf(eventAt), phone.removalsOf(atro0800))
        assertTrue(phone.alerts.all { it.at < eventAt }, "no alert after the delete")
        assertNull(phone.alarm)
    }

    @Test
    fun `removing a medicine removes its reminder at once, and the other carries on`() {
        val amoxicillinOnly = exampleWith { filter { it.id == Example.amoxicillinId } }
        val phone = morningWith(instant(nov(23, 12)), saving(amoxicillinOnly), "medicine removed")

        assertEquals(listOf(eventAt), phone.removalsOf(atro0800))
        assertEquals(instants(nov(23, 8), nov(23, 8, 10)), phone.alertsOf(atro0800))
        assertEquals(instants(nov(23, 8), nov(23, 8, 10), nov(23, 8, 20), nov(23, 8, 30)), phone.alertsOf(amox0800))
    }

    @Test
    fun `ending the treatment removes both reminders at once, and nothing wakes the app after`() {
        val phone =
            morningWith(
                instant(nov(23, 12)),
                { at -> change(at) { data, now -> checkNotNull(data.endTreatment(Example.treatmentId, now)) } },
                "end",
            )

        assertEquals(listOf(eventAt), phone.removalsOf(amox0800))
        assertEquals(listOf(eventAt), phone.removalsOf(atro0800))
        assertTrue(phone.alerts.all { it.at < eventAt }, "no alert after the end")
        assertNull(phone.alarm)
    }

    @Test
    fun `moving west removes both reminders, and they alert again when the takes fall due on the new clock`() {
        val london = ZoneId.of("Europe/London")
        val phone = morningWith(instant(nov(23, 12)), { at -> moveTo(london, at) }, "west")
        val again = listOf(nov(23, 8), nov(23, 8, 10), nov(23, 8, 20), nov(23, 8, 30)).map { it.atZone(london).toInstant() }

        for (key in listOf(amox0800, atro0800)) {
            assertEquals(instants(nov(23, 8), nov(23, 8, 10)) + again, phone.alertsOf(key), show(key))
            assertEquals(listOf(eventAt), phone.removalsOf(key), show(key))
        }
    }

    @Test
    fun `moving east keeps both reminders and their repeats, and they go at the new clock's Missed time`() {
        val athens = ZoneId.of("Europe/Athens")
        val phone = morningWith(instant(nov(23, 16)), { at -> moveTo(athens, at) }, "east")
        val missed = nov(23, 16).atZone(athens).toInstant()

        assertEquals(instant(nov(23, 15)), missed)
        for (key in listOf(amox0800, atro0800)) {
            assertEquals(instants(nov(23, 8), nov(23, 8, 10), nov(23, 8, 20), nov(23, 8, 30)), phone.alertsOf(key), show(key))
            assertEquals(listOf(missed), phone.removalsOf(key), show(key))
        }
        assertEquals(
            instants(nov(23, 15), nov(23, 15, 10), nov(23, 15, 20), nov(23, 15, 30)),
            phone.alertsOf(TakeKey(Example.amoxicillinId, nov(23, 16))),
        )
    }
}
