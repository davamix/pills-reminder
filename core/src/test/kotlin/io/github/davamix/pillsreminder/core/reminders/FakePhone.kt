package io.github.davamix.pillsreminder.core.reminders

import io.github.davamix.pillsreminder.core.MADRID
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.actions.skip
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.time.dueAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// The walks' harness (SPEC §6): a fake phone that applies plans as P5 will, and two oracles that work out the expected
// alerts and removals from the schedule alone, without the planner or P2's take states.

/** A reminder alerting, or leaving the shade, at an instant: what a walk logs. */
data class Logged(
    val at: Instant,
    val key: TakeKey,
) {
    override fun toString() = "${show(key)} at ${at.atZone(MADRID)}"
}

/** What the user does with a reminder, [after] it appears in the shade. */
data class Response(
    val after: Duration,
    val kind: Kind,
) {
    enum class Kind { TAKEN, SKIP, SNOOZE }
}

/** Something that happens to the phone during a walk, such as an edit or a change of zone. */
class Event(
    val at: Instant,
    val action: FakePhone.(Instant) -> Unit,
)

fun show(key: TakeKey) = "${key.medicineId.value} ${key.plannedAt}"

/**
 * Plays the part of P5 and the phone (SPEC §4.3.7, §6). It holds the data, the reminder state, the shade (a set of take
 * keys) and the alarm. Each reconcile applies a plan and logs its alerts and removals, after checking the plan's
 * properties (`assertPlanProperties`) and that no round alerts more than 4 times, or twice within 10 minutes.
 */
class FakePhone(
    data: PillsData,
    zone: ZoneId = MADRID,
    private val context: String = "",
) {
    var data: PillsData = data
        private set
    var zone: ZoneId = zone
        private set
    var reminders = ReminderState()
        private set
    var shade: Set<TakeKey> = emptySet()
        private set
    var alarm: Instant? = null
        private set

    val alerts = mutableListOf<Logged>()
    val removals = mutableListOf<Logged>()
    val reconciles = mutableListOf<Instant>()

    /** Each take's alerts in its current round, and the record time of the last. */
    private val rounds = mutableMapOf<TakeKey, Pair<Int, Instant>>()

    private var responses: (TakeKey) -> List<Response> = { emptyList() }
    private val remaining = mutableMapOf<TakeKey, List<Response>>()

    /** The next response to each reminder in the shade, and when it comes. */
    private val nextResponse = mutableMapOf<TakeKey, Pair<Instant, Response>>()
    private var alarmDelay: ((Instant) -> Duration)? = null
    private var fireFor: Instant? = null
    private var fireAt: Instant? = null

    /** Applies the plan at [at], as P5's reconcile will (SPEC §4.3.7). */
    fun reconcile(at: Instant): ReminderPlan {
        val now = at.atZone(zone)
        val where = "$context, at $now"
        val plan = ReminderPlanner.plan(data, reminders, now)
        assertPlanProperties(data, plan, now, where)
        val before = shade
        val recordTime = at.truncatedTo(ChronoUnit.SECONDS)
        for (key in plan.alerts) {
            val round = rounds[key]
            if (key in before && round != null) {
                val (count, last) = round
                assertTrue(count < 4, "$where: ${show(key)} alerts a fifth time in its round")
                assertTrue(recordTime >= last + Duration.ofMinutes(10), "$where: ${show(key)} alerts again within 10 minutes")
                rounds[key] = count + 1 to recordTime
            } else {
                rounds[key] = 1 to recordTime
            }
            alerts += Logged(at, key)
        }
        plan.removals(before).forEach { removals += Logged(at, it) }
        shade = plan.reminders.map { it.key }.toSet()
        reminders = plan.recorded
        alarm = plan.nextWakeUp
        reconciles += at
        for (key in before - shade) nextResponse.remove(key)
        for (key in shade - before) {
            val next = remaining.getOrPut(key) { responses(key) }.firstOrNull() ?: continue
            nextResponse[key] = at + next.after to next
        }
        if (alarm != fireFor) {
            fireFor = alarm
            fireAt = alarm?.let { it + (alarmDelay?.invoke(it) ?: Duration.ZERO) }
        }
        return plan
    }

    /** The user answers or snoozes the reminder of [key] at [at], then the app reconciles. */
    fun respond(
        key: TakeKey,
        kind: Response.Kind,
        at: Instant,
    ) {
        val now = at.atZone(zone)
        val refused = "$context: ${kind.name} refused for ${show(key)} at $now"
        when (kind) {
            Response.Kind.TAKEN -> data = checkNotNull(data.markTaken(key, now)) { refused }
            Response.Kind.SKIP -> data = checkNotNull(data.skip(key, now)) { refused }
            Response.Kind.SNOOZE -> reminders = checkNotNull(reminders.snooze(data, key, now)) { refused }
        }
        reconcile(at)
    }

    /** The data changes at [at], for example by an edit, then the app reconciles. */
    fun change(
        at: Instant,
        transform: (PillsData, ZonedDateTime) -> PillsData,
    ) {
        data = transform(data, at.atZone(zone))
        reconcile(at)
    }

    /** The phone moves to [zone] at [at], and the app reconciles (`TIMEZONE_CHANGED`, plan D-9). */
    fun moveTo(
        zone: ZoneId,
        at: Instant,
    ) {
        this.zone = zone
        reconcile(at)
    }

    /**
     * Runs the phone from [from] until [until]. The app opens at [from]. Then, in order of time: the alarm fires at the
     * next wake-up, late by [alarmDelay] if given; the app reconciles at each of [randomReconciles]; the user gives each
     * reminder the [responses] for its take, one at a time, each its `after` once the reminder appears; and each of
     * [events] happens. With exact alarms, a reconcile at a random moment must change nothing (SPEC §4.3.6).
     */
    fun walk(
        from: Instant,
        until: Instant,
        randomReconciles: List<Instant> = emptyList(),
        responses: (TakeKey) -> List<Response> = { emptyList() },
        events: List<Event> = emptyList(),
        alarmDelay: ((Instant) -> Duration)? = null,
    ) {
        this.responses = responses
        this.alarmDelay = alarmDelay
        val randoms = ArrayDeque(randomReconciles.filter { it >= from && it < until }.sorted())
        val pending = ArrayDeque(events.sortedBy { it.at })
        reconcile(from)
        while (true) {
            val alarmAt = fireAt
            val randomAt = randoms.firstOrNull()
            val response = nextResponse.entries.minByOrNull { it.value.first }
            val responseAt = response?.value?.first
            val eventAt = pending.firstOrNull()?.at
            val t = listOfNotNull(alarmAt, randomAt, responseAt, eventAt).minOrNull() ?: break
            if (t >= until) break
            when (t) {
                alarmAt -> reconcile(t)
                randomAt -> randomReconcile(randoms.removeFirst())
                responseAt -> {
                    val key = checkNotNull(response).key
                    nextResponse.remove(key)
                    remaining[key] = remaining.getValue(key).drop(1)
                    respond(key, response.value.second.kind, t)
                }
                else -> pending.removeFirst().action(this, t)
            }
        }
    }

    private fun randomReconcile(at: Instant) {
        val before = Triple(shade, reminders, alarm)
        val plan = reconcile(at)
        if (alarmDelay == null) {
            assertEquals(emptyList<TakeKey>(), plan.alerts, "$context: a reconcile at a random moment, $at, alerts nothing")
            assertEquals(before, Triple(shade, reminders, alarm), "$context: a reconcile at a random moment, $at, changes nothing")
        }
    }
}

/** A take as the oracles see it: when it falls due, and when it becomes Missed. */
data class OracleTake(
    val key: TakeKey,
    val due: Instant,
    val missed: Instant,
)

/**
 * The takes of a medicine that was never edited, falling due before [until], from its schedule alone (spec §4.3 rules
 * 1–3) and not from P2's code: take `k` at the first take plus `k` intervals, Missed one interval later. If its
 * treatment was ended at [endedAt], the takes after it are gone, and a take Due then is Missed at once.
 */
fun Medicine.oracleTakes(
    zone: ZoneId,
    until: Instant,
    endedAt: Instant? = null,
): List<OracleTake> {
    val step = schedule.interval.minutes
    val period =
        when (val duration = schedule.duration) {
            is MedicineDuration.Days -> duration.count * 1_440L
            is MedicineDuration.Weeks -> duration.count * 10_080L
            MedicineDuration.NoEnd -> null
        }
    val count = period?.let { (it + step - 1) / step } ?: Long.MAX_VALUE
    val takes = mutableListOf<OracleTake>()
    var k = 0L
    while (k < count) {
        val planned = schedule.firstTake.plusMinutes(k * step)
        val due = planned.dueAt(zone)
        if (due >= until || (endedAt != null && due > endedAt)) break
        val missed = planned.plusMinutes(step).dueAt(zone)
        takes += OracleTake(TakeKey(id, planned), due, if (endedAt == null) missed else minOf(missed, endedAt))
        k++
    }
    return takes
}

private val TEN_MINUTES: Duration = Duration.ofMinutes(10)

/**
 * The alerts and removals before [until] that the spec expects for this take with exact alarms, when the user gives
 * its reminder [responses] (`RM-3`, `RM-5`, `RM-7`): a round starts when the take falls due, and again 10 minutes after
 * a snooze; it alerts at its start and every 10 minutes after, 4 times at most, until the next response or the Missed
 * time. The reminder leaves the shade at each response and at the Missed time.
 */
fun OracleTake.expected(
    responses: List<Response>,
    until: Instant,
): Pair<List<Logged>, List<Logged>> {
    val alerts = mutableListOf<Instant>()
    val removals = mutableListOf<Instant>()
    var start = due
    var left = responses
    while (start < missed) {
        val response = left.firstOrNull()
        val action = response?.let { start + it.after }?.takeIf { it < missed }
        val roundEnd = action ?: missed
        for (k in 0L until 4L) {
            val alert = start + TEN_MINUTES.multipliedBy(k)
            if (alert < roundEnd) alerts += alert
        }
        removals += roundEnd
        if (action == null || response.kind != Response.Kind.SNOOZE) break
        start = action.truncatedTo(ChronoUnit.SECONDS) + TEN_MINUTES
        left = left.drop(1)
    }
    return alerts.filter { it < until }.map { Logged(it, key) } to removals.filter { it < until }.map { Logged(it, key) }
}

/**
 * With no response, the alerts and removals the spec expects for this take, given every moment the phone reconciled
 * ([reconciles], in order), on time or not (`RM-5`, P4-D3): it alerts at the first reconcile at or after it falls due,
 * then at the first reconcile at least 10 minutes after each alert, 4 times at most, while it isn't Missed. Its reminder
 * leaves at the first reconcile at or after its Missed time.
 */
fun OracleTake.expectedAt(reconciles: List<Instant>): Pair<List<Logged>, List<Logged>> {
    val alerts = mutableListOf<Instant>()
    var last: Instant? = null
    for (moment in reconciles.filter { it >= due && it < missed }) {
        val recordTime = moment.truncatedTo(ChronoUnit.SECONDS)
        if (last == null || (alerts.size < 4 && recordTime >= last + TEN_MINUTES)) {
            alerts += moment
            last = recordTime
        }
    }
    val removal = if (alerts.isEmpty()) null else reconciles.firstOrNull { it >= missed }
    return alerts.map { Logged(it, key) } to listOfNotNull(removal).map { Logged(it, key) }
}

/** Checks that a walk logged exactly the [expected] events, naming the first difference. */
fun assertSameLog(
    expected: List<Logged>,
    actual: List<Logged>,
    what: String,
) {
    val order = compareBy<Logged>({ it.at }, { it.key.medicineId.value }, { it.key.plannedAt })
    val wanted = expected.sortedWith(order)
    val logged = actual.sortedWith(order)
    if (wanted == logged) return
    val i = wanted.indices.firstOrNull { it >= logged.size || wanted[it] != logged[it] } ?: wanted.size
    val difference = "expected ${wanted.getOrNull(i)}, logged ${logged.getOrNull(i)}"
    fail<Unit>("$what: ${wanted.size} expected, ${logged.size} logged. First difference, #$i: $difference")
}
