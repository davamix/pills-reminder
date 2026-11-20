package io.github.davamix.pillsreminder.core.model

/**
 * Everything the repository stores: the treatments, and the answers apart from them, keyed by medicine and planned
 * time (P2-D5). P3 turns it into JSON. Reminder state (snoozes, alerts played) isn't here: P4 defines it.
 */
data class PillsData(
    val treatments: List<Treatment> = emptyList(),
    val answers: Map<TakeKey, Answer> = emptyMap(),
)

/** The treatment with this ID, or `null`. */
fun PillsData.treatment(id: TreatmentId): Treatment? = treatments.firstOrNull { it.id == id }

/** The medicine with this ID and its treatment, or `null`. */
fun PillsData.medicine(id: MedicineId): Pair<Treatment, Medicine>? {
    for (treatment in treatments) {
        val medicine = treatment.medicines.firstOrNull { it.id == id }
        if (medicine != null) return treatment to medicine
    }
    return null
}
