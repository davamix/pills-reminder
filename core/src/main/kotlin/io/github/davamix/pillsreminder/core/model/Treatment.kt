package io.github.davamix.pillsreminder.core.model

import java.time.LocalDateTime

// toString() of these classes includes treatment content (names, doses, instructions, notes). Never put one in a log
// or in an exception message (plan §3).

/** One medicine of a treatment (TR-3, TR-4, TR-6, TR-7). */
data class Medicine(
    val id: MedicineId,
    val name: String,
    /** Free text; may be empty, because TR-8 checks only the names. */
    val dose: String,
    /** Empty: none. */
    val instructions: String,
    /** The current schedule. */
    val schedule: Schedule,
    /** The schedules it had before it was edited after starting, oldest first (TR-10). */
    val earlierSchedules: List<EarlierSchedule> = emptyList(),
) {
    init {
        require(earlierSchedules.zipWithNext().all { (a, b) -> a.until < b.until }) {
            "Medicine ${id.value}: earlier schedules must be in order of their end"
        }
    }
}

/** A named group of medicines prescribed together (TR-2, TR-5). */
data class Treatment(
    val id: TreatmentId,
    val name: String,
    /** Empty: none. */
    val notes: String,
    val medicines: List<Medicine>,
    /** When it was ended early (TR-11), or `null`. */
    val endedAt: LocalDateTime? = null,
)
