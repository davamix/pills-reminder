package io.github.davamix.pillsreminder.core.reminders

import io.github.davamix.pillsreminder.core.MADRID
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.core.time.dueAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The instant this local time falls due in [zone]; in the repeated hour, the earlier one. */
fun instant(
    local: LocalDateTime,
    zone: ZoneId = MADRID,
): Instant = local.dueAt(zone)

/** The Due take of each medicine at [now]. */
fun PillsData.dueTakes(now: ZonedDateTime): Set<TakeKey> =
    treatments
        .flatMap { it.medicines }
        .mapNotNull { medicine -> takesOf(medicine.id)?.nextTake(now)?.takeIf { it.state == TakeState.DUE }?.key }
        .toSet()

/**
 * Checks the properties of SPEC §4.3.10 that hold for one plan, made from [data] at [now]:
 * - every reminder is a Due take, once, in order of planned time; every Due take is in it unless snoozed;
 * - the plan records every Due take and nothing else: snoozed ones as `Snoozed`, the others as `Alerted`;
 * - the next wake-up is after now;
 * - a plan on its own record, at now and at moments before the next wake-up, alerts nothing and changes nothing.
 */
fun assertPlanProperties(
    data: PillsData,
    plan: ReminderPlan,
    now: ZonedDateTime,
    context: String = "at $now",
) {
    val due = data.dueTakes(now)
    val shown = plan.reminders.map { it.key }
    val instant = now.toInstant()
    assertEquals(shown.toSet().size, shown.size, "$context: no reminder twice")
    assertEquals(shown.sortedBy { it.plannedAt }, shown, "$context: in order of planned time")
    assertTrue(due.containsAll(shown), "$context: every reminder is a Due take")
    assertEquals(due, plan.recorded.takes.keys, "$context: the plan records the Due takes, and only them")
    for (key in due) {
        val record = plan.recorded.takes.getValue(key)
        val snoozed = record is TakeReminder.Snoozed && record.until > instant
        assertEquals(!snoozed, key in shown, "$context: ${key.medicineId.value} at ${key.plannedAt} is shown unless snoozed")
        if (key in shown) assertTrue(record is TakeReminder.Alerted, "$context: a shown take's record is Alerted")
    }
    plan.nextWakeUp?.let { assertTrue(it > instant, "$context: the next wake-up $it is after now") }

    val wakeUp = plan.nextWakeUp ?: instant.plus(Duration.ofDays(1))
    val half = Duration.between(instant, wakeUp).dividedBy(2)
    for (moment in listOf(instant, instant + half, wakeUp.minusMillis(1)).distinct().filter { it >= instant }) {
        val again = ReminderPlanner.plan(data, plan.recorded, moment.atZone(now.zone))
        val at = "$context, planned again at $moment"
        assertEquals(emptyList<TakeKey>(), again.alerts, "$at: no alert")
        assertEquals(shown, again.reminders.map { it.key }, "$at: the same reminders")
        assertEquals(plan.nextWakeUp, again.nextWakeUp, "$at: the same next wake-up")
        assertEquals(plan.recorded, again.recorded, "$at: the same records")
    }
}
