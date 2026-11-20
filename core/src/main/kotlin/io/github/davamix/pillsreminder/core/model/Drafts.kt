package io.github.davamix.pillsreminder.core.model

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** The units a duration can have (TR-3). */
enum class DurationUnit { DAYS, WEEKS }

/**
 * A medicine as the editor holds it (P6): the amounts may be missing or out of range until [validate] passes.
 * A new medicine gets its ID from [MedicineId.random] when the editor creates it.
 */
data class MedicineDraft(
    val id: MedicineId,
    val name: String,
    val dose: String,
    val instructions: String,
    val firstTake: LocalDateTime,
    /** `null`: empty, or not a whole number that fits in an Int (P2-D10). */
    val intervalAmount: Int?,
    val intervalUnit: IntervalUnit,
    /** Ignored when [noEnd]. */
    val durationAmount: Int?,
    val durationUnit: DurationUnit,
    val noEnd: Boolean,
)

/** A treatment as the editor holds it (P6). A new treatment gets its ID from [TreatmentId.random]. */
data class TreatmentDraft(
    val id: TreatmentId,
    val name: String,
    val notes: String,
    val medicines: List<MedicineDraft>,
)

/** The draft to edit this treatment with. */
fun Treatment.toDraft(): TreatmentDraft = TreatmentDraft(id, name, notes, medicines.map { it.toDraft() })

/** The draft to edit this medicine with: its current schedule. */
fun Medicine.toDraft(): MedicineDraft {
    val (amount, unit) =
        when (val duration = schedule.duration) {
            is MedicineDuration.Days -> duration.count to DurationUnit.DAYS
            is MedicineDuration.Weeks -> duration.count to DurationUnit.WEEKS
            MedicineDuration.NoEnd -> null to DurationUnit.DAYS
        }
    return MedicineDraft(
        id = id,
        name = name,
        dose = dose,
        instructions = instructions,
        firstTake = schedule.firstTake,
        intervalAmount = schedule.interval.amount,
        intervalUnit = schedule.interval.unit,
        durationAmount = amount,
        durationUnit = unit,
        noEnd = schedule.duration == MedicineDuration.NoEnd,
    )
}

/** The schedule of a valid draft, with its first take truncated to whole minutes. */
fun MedicineDraft.toSchedule(): Schedule {
    val interval = requireNotNull(intervalAmount) { "Medicine ${id.value}: the interval is missing" }
    val duration =
        when {
            noEnd -> MedicineDuration.NoEnd
            else -> {
                val count = requireNotNull(durationAmount) { "Medicine ${id.value}: the duration is missing" }
                when (durationUnit) {
                    DurationUnit.DAYS -> MedicineDuration.Days(count)
                    DurationUnit.WEEKS -> MedicineDuration.Weeks(count)
                }
            }
        }
    return Schedule(firstTake.truncatedTo(ChronoUnit.MINUTES), Interval(interval, intervalUnit), duration)
}
