package io.github.davamix.pillsreminder.data

import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.EarlierSchedule
import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.Schedule
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.reminders.ReminderState
import io.github.davamix.pillsreminder.core.reminders.TakeReminder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

// Test data for the store (P3-D5): spec §4.4, with made-up IDs, and made-up data that uses every field of the format.
// The repo is public: no real treatments.

val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

/** A local date-time in November 2026, the month of the §4.4 example. Monday 23 November is its first day. */
fun nov(
    day: Int,
    hour: Int,
    minute: Int = 0,
): LocalDateTime = LocalDateTime.of(2026, 11, day, hour, minute)

fun nowAt(local: LocalDateTime): ZonedDateTime = local.atZone(MADRID)

fun clockAt(local: LocalDateTime): Clock = Clock.fixed(nowAt(local).toInstant(), MADRID)

fun schedule(
    firstTake: LocalDateTime,
    amount: Int,
    unit: IntervalUnit,
    duration: MedicineDuration,
) = Schedule(firstTake, Interval(amount, unit), duration)

object StoreExample {
    val treatmentId = TreatmentId("example")
    val amoxicillinId = MedicineId("amoxicillin")
    val atroventId = MedicineId("atrovent")

    /** Amoxicillin: 1 capsule every 8 h for 7 days, first take Monday 08:00. */
    val amoxicillin =
        Medicine(
            id = amoxicillinId,
            name = "Amoxicillin",
            dose = "1 capsule",
            instructions = "",
            schedule = schedule(nov(23, 8), 8, IntervalUnit.HOURS, MedicineDuration.Days(7)),
        )

    /** Atrovent: 3 puffs every 8 h for 5 days, first take Monday 08:00. */
    val atrovent =
        Medicine(
            id = atroventId,
            name = "Atrovent",
            dose = "3 puffs",
            instructions = "",
            schedule = schedule(nov(23, 8), 8, IntervalUnit.HOURS, MedicineDuration.Days(5)),
        )

    val treatment = Treatment(treatmentId, "Example", "", listOf(amoxicillin, atrovent))

    /** Spec §4.4, with Amoxicillin's 08:00 take Taken at 08:05 and Atrovent's skipped, as in P3 SPEC §4.3.1. */
    val example =
        PillsData(
            treatments = listOf(treatment),
            answers =
                mapOf(
                    TakeKey(amoxicillinId, nov(23, 8)) to Answer.Taken(nov(23, 8, 5)),
                    TakeKey(atroventId, nov(23, 8)) to Answer.Skipped,
                ),
        )

    val endedId = TreatmentId("ended")
    val weeklyId = MedicineId("weekly")
    val minutesId = MedicineId("minutes")

    /** Text that JSON must escape or encode: accents, ½, quotes, a backslash, a new line, a tab and an emoji. */
    const val AWKWARD_TEXT = "Después de comer, ½ \"dose\" \\ new\nline\tand 💊"

    /**
     * Weekly, with no end, edited twice after it started (TR-10). The first earlier schedule holds the takes of 16, 18
     * and 20 November; the second holds none, as P2's `saveTreatment` can store.
     */
    val weekly =
        Medicine(
            id = weeklyId,
            name = "Weekly ñandú",
            dose = "½ tablet",
            instructions = AWKWARD_TEXT,
            schedule = schedule(nov(23, 9), 1, IntervalUnit.WEEKS, MedicineDuration.NoEnd),
            earlierSchedules =
                listOf(
                    EarlierSchedule(schedule(nov(16, 9), 2, IntervalUnit.DAYS, MedicineDuration.Weeks(2)), until = nov(20, 9)),
                    EarlierSchedule(schedule(nov(22, 12), 12, IntervalUnit.HOURS, MedicineDuration.Days(3)), until = nov(22, 10)),
                ),
        )

    /** Every 30 minutes for a day: debug builds only (plan D-15). */
    val minutes =
        Medicine(
            id = minutesId,
            name = "Minutes",
            dose = "",
            instructions = "",
            schedule = schedule(nov(24, 7, 15), 30, IntervalUnit.MINUTES, MedicineDuration.Days(1)),
        )

    /** Ended early (TR-11), so Finished; kept until deleted (TR-12). */
    val ended = Treatment(endedId, "Ended example", AWKWARD_TEXT, listOf(weekly, minutes), endedAt = nov(25, 12, 30))

    /** Every field and variant of the format. `pills-v1.json` holds exactly this. */
    val full =
        PillsData(
            treatments = listOf(treatment, ended),
            answers =
                example.answers +
                    mapOf(
                        TakeKey(weeklyId, nov(16, 9)) to Answer.Taken(nov(16, 9)),
                        TakeKey(weeklyId, nov(23, 9)) to Answer.Taken(nov(23, 9, 2)),
                        TakeKey(minutesId, nov(24, 7, 15)) to Answer.Skipped,
                        TakeKey(minutesId, nov(24, 7, 45)) to Answer.Taken(nov(24, 7, 50)),
                    ),
        )

    /**
     * The example on Monday at 16:10 in Madrid, with both kinds of reminder record (P5 SPEC §4.3.7): Amoxicillin's
     * 16:00 reminder has alerted twice, the last time at 16:10; Atrovent's was snoozed at 16:05. `pills-v1-reminders.json`
     * holds exactly this.
     */
    val reminded =
        example.copy(
            reminders =
                ReminderState(
                    mapOf(
                        TakeKey(amoxicillinId, nov(23, 16)) to TakeReminder.Alerted(2, Instant.parse("2026-11-23T15:10:00Z")),
                        TakeKey(atroventId, nov(23, 16)) to TakeReminder.Snoozed(Instant.parse("2026-11-23T15:15:00Z")),
                    ),
                ),
        )

    /** The names, doses, instructions and notes in [full], none of which may appear in a log or a reason. */
    val content: List<String> =
        (
            full.treatments.flatMap { t ->
                listOf(t.name, t.notes) + t.medicines.flatMap { listOf(it.name, it.dose, it.instructions) }
            } + listOf("Amoxicillin", "capsule", "puffs", "Después", "ñandú")
        ).filter { it.isNotBlank() }.distinct()
}
