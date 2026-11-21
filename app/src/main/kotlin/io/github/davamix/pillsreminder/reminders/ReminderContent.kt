package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.medicine
import io.github.davamix.pillsreminder.core.time.dueAt
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/**
 * What a take's reminder says (RM-1, TR-6), as plain values. The Android shade formats them with the strings of
 * `strings_reminders.xml`: "Amoxicillin: 1 capsule", and "Example · with food".
 *
 * Its `toString()` holds treatment content: never log it or put it in a message (plan §3).
 */
data class ReminderContent(
    val key: TakeKey,
    val medicine: String,
    val dose: String,
    val treatment: String,
    /** Empty when the medicine has none. */
    val instructions: String,
    /** When the take fell due, on the clock of the zone it was worked out in: the notification's time (P5-D7). */
    val dueAt: Instant,
) {
    /**
     * A digest of everything the reminder shows, kept with the posted notification. A different one means the text
     * changed, for example after an edit, and a silent reminder must be posted again (P5-D5). It reveals no content.
     */
    val fingerprint: String by lazy {
        val text = listOf(medicine, dose, treatment, instructions, dueAt.toString()).joinToString("\u0000")
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.encodeToByteArray())
            .take(FINGERPRINT_BYTES)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private companion object {
        const val FINGERPRINT_BYTES = 12
    }
}

/** The content of the take's reminder in [zone], or `null` when its medicine is no longer in any treatment. */
fun PillsData.reminderContent(
    key: TakeKey,
    zone: ZoneId,
): ReminderContent? {
    val (treatment, medicine) = medicine(key.medicineId) ?: return null
    return ReminderContent(
        key = key,
        medicine = medicine.name,
        dose = medicine.dose,
        treatment = treatment.name,
        instructions = medicine.instructions,
        dueAt = key.plannedAt.dueAt(zone),
    )
}
