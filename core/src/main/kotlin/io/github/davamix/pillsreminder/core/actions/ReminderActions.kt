package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.reminders.ReminderState
import io.github.davamix.pillsreminder.core.reminders.snooze
import java.time.ZonedDateTime

// The reminder state's changes on PillsData (P5 SPEC §4.2). The reconcile stores the planner's records itself; the only
// change a user makes is a snooze.

/**
 * Snoozes the take's reminder (RM-3): it leaves the shade, and a new round of alerts starts 10 minutes after now.
 * `null`, changing nothing, when P4's snooze refuses: the take isn't Due at [now], or is already snoozed.
 */
fun PillsData.snooze(
    key: TakeKey,
    now: ZonedDateTime,
): PillsData? = reminders.snooze(this, key, now)?.let { copy(reminders = it) }

/** The reminder state without the records of these medicines, which were deleted or removed (P5-D8). */
internal fun ReminderState.without(medicines: Set<MedicineId>): ReminderState =
    ReminderState(takes.filterKeys { it.medicineId !in medicines })
