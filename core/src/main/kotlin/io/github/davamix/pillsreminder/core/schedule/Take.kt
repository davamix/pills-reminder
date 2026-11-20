package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.model.TakeKey
import java.time.LocalDateTime

/** The state of a take (TK-1). Worked out from the answers, the schedule and the time now; never stored (plan D-5). */
enum class TakeState { PENDING, DUE, TAKEN, SKIPPED, MISSED }

/** One take of a medicine, as it stands at a given moment. */
data class Take(
    val key: TakeKey,
    val state: TakeState,
    /** When it was taken, for a [TakeState.TAKEN] take (TK-2). */
    val takenAt: LocalDateTime?,
    /** When it becomes, or became, Missed if it is left unanswered (TK-3). */
    val missedAt: LocalDateTime,
)
