package io.github.davamix.pillsreminder.core.reminders

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.take
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * What the store remembers about reminders (plan §2.4): a record for each Due take that has alerted or is snoozed.
 * Pending, answered and Missed takes have none: the planner drops their records (P4-D4). P5 adds it to `PillsData` and
 * to the JSON format (P4-D6).
 */
data class ReminderState(
    val takes: Map<TakeKey, TakeReminder> = emptyMap(),
)

/** A Due take's reminder record. Its times are instants in whole seconds (P4-D5). */
sealed interface TakeReminder {
    /** Snoozed (RM-3): out of the shade until [until], when a new round of alerts starts. */
    data class Snoozed(
        val until: Instant,
    ) : TakeReminder {
        init {
            requireWholeSeconds(until)
        }
    }

    /** In the shade, having alerted [count] times in its current round (1 to 4), the last at [lastAt] (RM-5). */
    data class Alerted(
        val count: Int,
        val lastAt: Instant,
    ) : TakeReminder {
        init {
            require(count in 1..ReminderPlanner.ALERTS_PER_ROUND) {
                "A round has 1 to ${ReminderPlanner.ALERTS_PER_ROUND} alerts, not $count"
            }
            requireWholeSeconds(lastAt)
        }
    }
}

private fun requireWholeSeconds(time: Instant) = require(time.nano == 0) { "A reminder record's time has whole seconds, not $time" }

/**
 * Snoozes the reminder of the take with this key (RM-3): it leaves the shade, and a new round of alerts starts
 * 10 minutes after now (SPEC §4.3.4). `null`, changing nothing, when the take isn't Due at [now], or is already snoozed.
 * A reminder that has finished its round can be snoozed: it then alerts again later.
 */
fun ReminderState.snooze(
    data: PillsData,
    key: TakeKey,
    now: ZonedDateTime,
): ReminderState? {
    if (data.take(key, now)?.state != TakeState.DUE) return null
    val record = takes[key]
    if (record is TakeReminder.Snoozed && record.until > now.toInstant()) return null
    return copy(takes = takes + (key to TakeReminder.Snoozed(now.recordTime() + ReminderPlanner.SNOOZE)))
}

/** Now as a record stores it: an instant in whole seconds (SPEC §4.3.1, P4-D5). */
internal fun ZonedDateTime.recordTime(): Instant = toInstant().truncatedTo(ChronoUnit.SECONDS)
