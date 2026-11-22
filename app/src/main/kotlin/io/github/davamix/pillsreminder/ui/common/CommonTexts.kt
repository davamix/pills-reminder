package io.github.davamix.pillsreminder.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.schedule.Take
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.TreatmentStatus
import java.time.LocalDate

// Texts that several screens show the same way, from strings_common.xml.

/** "Amoxicillin · 1 capsule", or the name alone without a dose. */
@Composable
fun medicineAndDose(
    name: String,
    dose: String,
): String = if (dose.isBlank()) name else stringResource(R.string.medicine_and_dose, name, dose)

/** "Every 8 hours", "Every week". */
@Composable
fun intervalText(interval: Interval): String {
    val plural =
        when (interval.unit) {
            IntervalUnit.MINUTES -> R.plurals.schedule_every_minutes
            IntervalUnit.HOURS -> R.plurals.schedule_every_hours
            IntervalUnit.DAYS -> R.plurals.schedule_every_days
            IntervalUnit.WEEKS -> R.plurals.schedule_every_weeks
        }
    return pluralStringResource(plural, interval.amount, interval.amount)
}

/** "for 7 days", "for 12 weeks", or "no end". */
@Composable
fun durationText(duration: MedicineDuration): String =
    when (duration) {
        is MedicineDuration.Days -> pluralStringResource(R.plurals.schedule_for_days, duration.count, duration.count)
        is MedicineDuration.Weeks -> pluralStringResource(R.plurals.schedule_for_weeks, duration.count, duration.count)
        MedicineDuration.NoEnd -> stringResource(R.string.schedule_no_end)
    }

/** "Every 8 hours, for 7 days", or "Every week, no end". */
@Composable
fun scheduleText(
    interval: Interval,
    duration: MedicineDuration,
): String = stringResource(R.string.schedule_summary, intervalText(interval), durationText(duration))

/** A take's state as a word: Pending, Due, Taken, Skipped or Missed (TK-1). */
@Composable
fun takeStateText(state: TakeState): String =
    stringResource(
        when (state) {
            TakeState.PENDING -> R.string.take_state_pending
            TakeState.DUE -> R.string.take_state_due
            TakeState.TAKEN -> R.string.take_state_taken
            TakeState.SKIPPED -> R.string.take_state_skipped
            TakeState.MISSED -> R.string.take_state_missed
        },
    )

/** A treatment's status as a word: Active, Upcoming or Finished (spec §4.3 rule 7). */
@Composable
fun statusText(status: TreatmentStatus): String =
    stringResource(
        when (status) {
            TreatmentStatus.ACTIVE -> R.string.status_active
            TreatmentStatus.UPCOMING -> R.string.status_upcoming
            TreatmentStatus.FINISHED -> R.string.status_finished
        },
    )

/**
 * When a Taken take was taken (TK-2): "Taken at 08:05" on the day it was planned, or "Taken: Sun 22 Nov 23:50" on
 * another. `null` for a take that isn't Taken.
 */
@Composable
fun takenText(
    take: Take,
    text: DateTimeText,
    today: LocalDate,
): String? {
    val at = take.takenAt ?: return null
    return if (at.toLocalDate() == take.key.plannedAt.toLocalDate()) {
        stringResource(R.string.taken_at, text.time(at))
    } else {
        stringResource(R.string.taken_on, text.dateTime(at, today))
    }
}

/** A take's state, and when it was taken: "Taken at 08:05", "Skipped", "Due". */
@Composable
fun takeOutcome(
    take: Take,
    text: DateTimeText,
    today: LocalDate,
): String = takenText(take, text, today) ?: takeStateText(take.state)
