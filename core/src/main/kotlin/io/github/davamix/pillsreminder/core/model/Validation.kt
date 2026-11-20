package io.github.davamix.pillsreminder.core.model

/** What is wrong with a draft (TR-8). P6 turns each one into a message. */
sealed interface ValidationError {
    data object TreatmentNameMissing : ValidationError

    data object NoMedicine : ValidationError

    data class MedicineNameMissing(
        val medicine: MedicineId,
    ) : ValidationError

    /** The interval amount is missing, or less than 1. */
    data class IntervalInvalid(
        val medicine: MedicineId,
    ) : ValidationError

    /** The interval is in minutes, which only debug builds allow (plan D-15). */
    data class MinutesNotAllowed(
        val medicine: MedicineId,
    ) : ValidationError

    /** The duration amount is missing, or less than 1, and the duration isn't "no end". */
    data class DurationInvalid(
        val medicine: MedicineId,
    ) : ValidationError
}

/**
 * The medicine's errors, all at once (TR-8). The dose, the instructions and the first take aren't checked: a first take
 * may be in the past (TR-4). `:app` passes `allowMinutes = BuildConfig.DEBUG`.
 */
fun MedicineDraft.validate(allowMinutes: Boolean): List<ValidationError> =
    buildList {
        if (name.isBlank()) add(ValidationError.MedicineNameMissing(id))
        if (intervalAmount == null || intervalAmount < 1) add(ValidationError.IntervalInvalid(id))
        if (intervalUnit == IntervalUnit.MINUTES && !allowMinutes) add(ValidationError.MinutesNotAllowed(id))
        if (!noEnd && (durationAmount == null || durationAmount < 1)) add(ValidationError.DurationInvalid(id))
    }

/** The treatment's own errors, then each medicine's, all at once (TR-8). */
fun TreatmentDraft.validate(allowMinutes: Boolean): List<ValidationError> =
    buildList {
        if (name.isBlank()) add(ValidationError.TreatmentNameMissing)
        if (medicines.isEmpty()) add(ValidationError.NoMedicine)
        medicines.forEach { addAll(it.validate(allowMinutes)) }
    }
