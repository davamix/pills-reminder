package io.github.davamix.pillsreminder.core.model

import io.github.davamix.pillsreminder.core.reminders.ReminderState

/**
 * Everything the repository stores: the treatments, and the answers apart from them, keyed by medicine and planned
 * time (P2-D5), and the reminder state. P3 turns it into JSON, with the reminder state since P5 (P5-D1).
 */
data class PillsData(
    val treatments: List<Treatment> = emptyList(),
    val answers: Map<TakeKey, Answer> = emptyMap(),
    /**
     * Snoozes and alerts played (P4): a record for each Due take that has alerted or is snoozed. The reconcile stores
     * the plan's `recorded` here (P5 SPEC §4.3.1). P4's type, left in `core.reminders` (P5-D10).
     */
    val reminders: ReminderState = ReminderState(),
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
