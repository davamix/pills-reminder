package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.treatment
import io.github.davamix.pillsreminder.core.time.dueAt
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** A treatment's status (spec §4.3 rule 7). */
enum class TreatmentStatus { UPCOMING, ACTIVE, FINISHED }

/**
 * The treatment's status at [now], or `null` if there is no such treatment (spec §4.3 rule 7):
 * Finished once ended early (TR-11); Upcoming until its first take falls due; Active while a take of any medicine is
 * Pending or Due; Finished after that. A Missed take counts as done. A medicine with no end keeps it Active.
 */
fun PillsData.status(
    treatmentId: TreatmentId,
    now: ZonedDateTime,
): TreatmentStatus? {
    val treatment = treatment(treatmentId) ?: return null
    if (treatment.endedAt != null) return TreatmentStatus.FINISHED
    val medicines = treatment.medicines.mapNotNull { takesOf(it.id) }
    val first = medicines.mapNotNull { it.first }.minOrNull()
    return when {
        first != null && first.dueAt(now.zone) > now.toInstant() -> TreatmentStatus.UPCOMING
        medicines.any { it.nextTake(now) != null } -> TreatmentStatus.ACTIVE
        else -> TreatmentStatus.FINISHED
    }
}

/** The earliest first take of the treatment's medicines (HM-4), or `null` if it has none or doesn't exist. */
fun PillsData.firstTake(treatmentId: TreatmentId): LocalDateTime? =
    treatment(treatmentId)?.medicines?.mapNotNull { takesOf(it.id)?.first }?.minOrNull()
