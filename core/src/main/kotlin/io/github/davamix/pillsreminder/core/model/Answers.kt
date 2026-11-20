package io.github.davamix.pillsreminder.core.model

import java.time.LocalDateTime

/**
 * Identifies a take: its medicine and its planned time, a local date-time in whole minutes. P1's `TakeRoute` carries
 * the same two values, as [MedicineId.value] and `plannedAt.toString()`, such as `2026-11-23T16:00`.
 */
data class TakeKey(
    val medicineId: MedicineId,
    val plannedAt: LocalDateTime,
)

/** The answer given to a take. Pending, Due and Missed aren't answers: they are worked out (plan D-5). */
sealed interface Answer {
    /** Taken, at [at] on the phone's clock, in whole minutes (TK-2). */
    data class Taken(
        val at: LocalDateTime,
    ) : Answer

    data object Skipped : Answer
}
