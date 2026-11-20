package io.github.davamix.pillsreminder.core.actions

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentDraft
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZonedDateTime

// The actions, run on the latest stored data inside TreatmentRepository.update (P2-D4). Each reads the clock once,
// before the update. Those that can be refused return false, and change nothing. All of them throw
// UnreadableDataException when the stored data can't be read.

/** Creates or edits the treatment (SPEC §4.3.6). The draft must be valid. */
suspend fun TreatmentRepository.saveTreatment(
    draft: TreatmentDraft,
    clock: Clock,
) {
    val now = ZonedDateTime.now(clock)
    update { it.saveTreatment(draft, now) }
}

/** Ends the treatment now (TR-11). */
suspend fun TreatmentRepository.endTreatment(
    id: TreatmentId,
    clock: Clock,
): Boolean {
    val now = ZonedDateTime.now(clock)
    return change { it.endTreatment(id, now) }
}

/** Deletes the treatment and its answers (TR-9). */
suspend fun TreatmentRepository.deleteTreatment(id: TreatmentId): Boolean = change { it.deleteTreatment(id) }

/** Marks the take Taken now (TK-2, TK-4, TK-5). */
suspend fun TreatmentRepository.markTaken(
    key: TakeKey,
    clock: Clock,
): Boolean {
    val now = ZonedDateTime.now(clock)
    return change { it.markTaken(key, now) }
}

/** Skips the take (TK-5). */
suspend fun TreatmentRepository.skip(
    key: TakeKey,
    clock: Clock,
): Boolean {
    val now = ZonedDateTime.now(clock)
    return change { it.skip(key, now) }
}

/** Clears the last answered take's answer (TK-5). */
suspend fun TreatmentRepository.clearAnswer(
    key: TakeKey,
    clock: Clock,
): Boolean {
    val now = ZonedDateTime.now(clock)
    return change { it.clearAnswer(key, now) }
}

/** Corrects the time the last answered take was taken (TK-2). */
suspend fun TreatmentRepository.setTakenTime(
    key: TakeKey,
    at: LocalDateTime,
    clock: Clock,
): Boolean {
    val now = ZonedDateTime.now(clock)
    return change { it.setTakenTime(key, at, now) }
}

/** Runs an action that returns `null` when refused, and says whether it was applied. */
private suspend fun TreatmentRepository.change(action: (PillsData) -> PillsData?): Boolean {
    var applied = false
    update { data ->
        val changed = action(data)
        applied = changed != null
        changed ?: data
    }
    return applied
}
