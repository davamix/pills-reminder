package io.github.davamix.pillsreminder.core.model

import java.util.UUID

/** Identifies a treatment. */
@JvmInline
value class TreatmentId(
    val value: String,
) {
    companion object {
        /** A new, random ID. */
        fun random(): TreatmentId = TreatmentId(UUID.randomUUID().toString())
    }
}

/** Identifies a medicine. Medicine IDs are unique across all treatments. */
@JvmInline
value class MedicineId(
    val value: String,
) {
    companion object {
        /** A new, random ID. */
        fun random(): MedicineId = MedicineId(UUID.randomUUID().toString())
    }
}
