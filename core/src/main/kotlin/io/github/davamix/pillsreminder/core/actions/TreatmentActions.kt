package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.EarlierSchedule
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDraft
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentDraft
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.medicine
import io.github.davamix.pillsreminder.core.model.toSchedule
import io.github.davamix.pillsreminder.core.model.treatment
import io.github.davamix.pillsreminder.core.model.validate
import io.github.davamix.pillsreminder.core.schedule.MedicineTakes
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Pure functions on PillsData (P2-D4). The repository runs them inside update(), on the latest stored data. Their
// exception messages name only error types, IDs and times, never treatment content (plan §3).

/**
 * Creates the treatment, or saves the edit of a stored one (SPEC §4.3.6). The draft must be valid.
 *
 * - A medicine whose schedule changed after it started keeps its takes up to the cut-off, the later of now and its
 *   last answered take, under an [EarlierSchedule]; the new schedule's takes after the cut-off replace the rest (TR-10).
 * - A stored medicine missing from the draft is removed with its answers.
 * - Names, dose, instructions and notes are saved trimmed.
 */
fun PillsData.saveTreatment(
    draft: TreatmentDraft,
    now: ZonedDateTime,
): PillsData {
    val errors = draft.validate(allowMinutes = true)
    require(errors.isEmpty()) { "Treatment ${draft.id.value} is invalid: ${errors.joinToString()}" }
    val ids = draft.medicines.map { it.id }
    require(ids.size == ids.toSet().size) { "Treatment ${draft.id.value} has a medicine twice" }
    for (id in ids) {
        val owner = medicine(id)?.first?.id
        require(owner == null || owner == draft.id) { "Medicine ${id.value} belongs to treatment ${owner?.value}" }
    }
    val stored = treatment(draft.id)
    val medicines = draft.medicines.map { merged(stored, it, now) }
    val treatment = Treatment(draft.id, draft.name.trim(), draft.notes.trim(), medicines, stored?.endedAt)
    val removed =
        stored
            ?.medicines
            .orEmpty()
            .map { it.id }
            .toSet() - ids.toSet()
    return copy(
        treatments = if (stored == null) treatments + treatment else treatments.map { if (it.id == draft.id) treatment else it },
        answers = answers.filterKeys { it.medicineId !in removed },
    )
}

/**
 * The takes the medicine would have if the treatment were saved now with this draft of it (SC-2), for the editor to
 * show its count and last take. The draft must be valid; the treatment and the medicine may be new.
 */
fun PillsData.previewMedicine(
    treatmentId: TreatmentId,
    draft: MedicineDraft,
    now: ZonedDateTime,
): MedicineTakes {
    val stored = treatment(treatmentId)
    return MedicineTakes(merged(stored, draft, now), stored?.endedAt, answersOf(draft.id))
}

/**
 * Ends the treatment now (TR-11): its takes after now go, apart from a take already answered early, and a take Due now
 * becomes Missed at once. `null` if there is no such treatment, or it is already ended.
 */
fun PillsData.endTreatment(
    id: TreatmentId,
    now: ZonedDateTime,
): PillsData? {
    val treatment = treatment(id)?.takeIf { it.endedAt == null } ?: return null
    val ended = treatment.copy(endedAt = now.wholeMinutes())
    return copy(treatments = treatments.map { if (it.id == id) ended else it })
}

/** Deletes the treatment and every answer of its medicines (TR-9). `null` if there is no such treatment. */
fun PillsData.deleteTreatment(id: TreatmentId): PillsData? {
    val treatment = treatment(id) ?: return null
    val medicines = treatment.medicines.map { it.id }.toSet()
    return copy(treatments = treatments - treatment, answers = answers.filterKeys { it.medicineId !in medicines })
}

/** The draft's medicine, merged with the stored one of the same ID, if any (SPEC §4.3.6). */
private fun PillsData.merged(
    stored: Treatment?,
    draft: MedicineDraft,
    now: ZonedDateTime,
): Medicine {
    val schedule = draft.toSchedule()
    val medicine = Medicine(draft.id, draft.name.trim(), draft.dose.trim(), draft.instructions.trim(), schedule)
    val old = stored?.medicines?.firstOrNull { it.id == draft.id } ?: return medicine
    if (old.schedule == schedule) return medicine.copy(earlierSchedules = old.earlierSchedules)
    val takes = MedicineTakes(old, stored?.endedAt, answersOf(old.id))
    val cutOff = listOfNotNull(now.wholeMinutes(), takes.answers.keys.maxOrNull()).max()
    if (takes.latestAtOrBefore(cutOff) == null) return medicine
    // The old schedule's window ends at the cut-off, even when it holds no take, so the new schedule never adds takes
    // before it.
    return medicine.copy(earlierSchedules = old.earlierSchedules + EarlierSchedule(old.schedule, until = cutOff))
}

internal fun PillsData.answersOf(medicineId: MedicineId): Map<LocalDateTime, Answer> =
    answers.filterKeys { it.medicineId == medicineId }.mapKeys { it.key.plannedAt }

internal fun ZonedDateTime.wholeMinutes(): LocalDateTime = toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
