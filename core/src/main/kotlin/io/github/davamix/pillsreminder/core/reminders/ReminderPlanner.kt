package io.github.davamix.pillsreminder.core.reminders

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.takesOf
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** Works out what the notification shade should hold at a given moment (plan §2.3, D-7). Pure: no clock, no state. */
object ReminderPlanner {
    /** How long a snooze lasts (RM-3). */
    val SNOOZE: Duration = Duration.ofMinutes(10)

    /** The time between two alerts of a round (RM-5). */
    val REPEAT_AFTER: Duration = Duration.ofMinutes(10)

    /** The alerts in a round: the first, then up to 3 repeats (RM-5). */
    const val ALERTS_PER_ROUND: Int = 4

    /**
     * The plan at [now], from the stored data and reminder state (SPEC §4.3).
     *
     * Each medicine's Due take is in the shade unless it is snoozed. It alerts when it has just fallen due, when a
     * snooze has ended, or when a repeat is due: 10 minutes after the last alert of its round, up to 4 alerts. The next
     * wake-up is the earliest of the takes' next changes of state, the snoozes' ends and the repeats.
     */
    fun plan(
        data: PillsData,
        reminders: ReminderState,
        now: ZonedDateTime,
    ): ReminderPlan {
        val instant = now.toInstant()
        val recordTime = now.recordTime()
        val shown = mutableListOf<Reminder>()
        val recorded = LinkedHashMap<TakeKey, TakeReminder>()
        val wakeUps = mutableListOf<Instant>()
        for (treatment in data.treatments) {
            for (medicine in treatment.medicines) {
                val takes = checkNotNull(data.takesOf(medicine.id)) { "Medicine ${medicine.id.value} has no takes" }
                takes.nextStateChange(now)?.let(wakeUps::add)
                val key = takes.nextTake(now)?.takeIf { it.state == TakeState.DUE }?.key ?: continue
                val record = reminders.takes[key]?.pulledBack(recordTime)
                val alert = alertsNow(record, instant)
                val after =
                    if (alert || record == null) {
                        TakeReminder.Alerted(((record as? TakeReminder.Alerted)?.count ?: 0) + 1, recordTime)
                    } else {
                        record
                    }
                recorded[key] = after
                if (after is TakeReminder.Alerted) shown += Reminder(key, alert)
                after.nextWakeUp()?.let(wakeUps::add)
            }
        }
        return ReminderPlan(shown.sortedBy { it.key.plannedAt }, wakeUps.minOrNull(), ReminderState(recorded))
    }

    /**
     * Whether a Due take with this record alerts at [now] (SPEC §4.3.3): it has no record yet, its snooze has ended, or
     * a repeat is due.
     */
    private fun alertsNow(
        record: TakeReminder?,
        now: Instant,
    ): Boolean =
        when (record) {
            null -> true
            is TakeReminder.Snoozed -> record.until <= now
            is TakeReminder.Alerted -> record.count < ALERTS_PER_ROUND && record.lastAt + REPEAT_AFTER <= now
        }

    /**
     * The record after the clock was set back (SPEC §4.3.8): no snooze ends more than 10 minutes after [recordTime],
     * and no alert played after it.
     */
    private fun TakeReminder.pulledBack(recordTime: Instant): TakeReminder =
        when (this) {
            is TakeReminder.Snoozed -> if (until > recordTime + SNOOZE) TakeReminder.Snoozed(recordTime + SNOOZE) else this
            is TakeReminder.Alerted -> if (lastAt > recordTime) copy(lastAt = recordTime) else this
        }

    /** When this record next needs a plan: the snooze's end, or the next repeat; `null` once the round is over. */
    private fun TakeReminder.nextWakeUp(): Instant? =
        when (this) {
            is TakeReminder.Snoozed -> until
            is TakeReminder.Alerted -> if (count < ALERTS_PER_ROUND) lastAt + REPEAT_AFTER else null
        }
}

/** What the shade should hold at a moment, and what to do next (plan §2.3). */
data class ReminderPlan(
    /** One per Due take that isn't snoozed, in order of planned time, then of treatments and medicines in the data. */
    val reminders: List<Reminder>,
    /** The earliest instant after now at which the plan would change by itself; `null` when nothing is left to wake for. */
    val nextWakeUp: Instant?,
    /** The reminder state to store once the plan is applied: its alerts recorded, records no longer needed dropped. */
    val recorded: ReminderState,
) {
    /** The takes whose reminders alert now. */
    val alerts: List<TakeKey> get() = reminders.filter { it.alert }.map { it.key }

    /** Of the reminders in the shade now, those that must go: every one this plan doesn't hold (P4-D2). */
    fun removals(inShade: Set<TakeKey>): Set<TakeKey> = inShade - reminders.map { it.key }.toSet()
}

/** A reminder the shade should hold. [alert]: post it with sound now; otherwise post or update it silently. */
data class Reminder(
    val key: TakeKey,
    val alert: Boolean,
)
