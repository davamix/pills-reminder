package io.github.davamix.pillsreminder.core

import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.Schedule
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentId
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

// Test data from spec §4.4, with made-up IDs. The repo is public: no real treatments.

val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

/** A local date-time in November 2026, the month of the §4.4 example. Monday 23 November is its first day. */
fun nov(
    day: Int,
    hour: Int,
    minute: Int = 0,
): LocalDateTime = LocalDateTime.of(2026, 11, day, hour, minute)

/** A local date-time in 2026. */
fun at(
    month: Int,
    day: Int,
    hour: Int,
    minute: Int = 0,
): LocalDateTime = LocalDateTime.of(2026, month, day, hour, minute)

/** "Now" at a local time in Europe/Madrid, or another zone. In the repeated hour, the earlier offset. */
fun nowAt(
    local: LocalDateTime,
    zone: ZoneId = MADRID,
): ZonedDateTime = local.atZone(zone)

fun schedule(
    firstTake: LocalDateTime,
    amount: Int,
    unit: IntervalUnit,
    duration: MedicineDuration,
) = Schedule(firstTake, Interval(amount, unit), duration)

object Example {
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

    val data = PillsData(listOf(treatment))
}
