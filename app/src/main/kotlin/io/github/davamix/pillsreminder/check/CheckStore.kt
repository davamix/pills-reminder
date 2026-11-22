package io.github.davamix.pillsreminder.check

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The record the check keeps on the phone (P7-D1): never treatment content. */
data class CheckRecord(
    /** The check has opened at the first launch, so it doesn't open again (P7 SPEC §4.3.10). */
    val firstLaunchDone: Boolean = false,
    /** The HyperOS steps the user has marked done (Q4). */
    val confirmed: Set<HyperOsStep> = emptySet(),
)

/** Where the check keeps its [CheckRecord], apart from the treatments (P7-D1). */
interface CheckStore {
    /** The record, then every change. */
    val record: Flow<CheckRecord>

    /**
     * Runs [transform] on the latest record and keeps the result.
     *
     * @throws java.io.IOException when the write fails; the record stays as it was.
     */
    suspend fun update(transform: (CheckRecord) -> CheckRecord)
}

/** A [CheckStore] in memory: for tests, like `:core`'s `InMemoryTreatmentRepository`. */
class InMemoryCheckStore(
    initial: CheckRecord = CheckRecord(),
) : CheckStore {
    private val state = MutableStateFlow(initial)

    override val record: StateFlow<CheckRecord> = state

    override suspend fun update(transform: (CheckRecord) -> CheckRecord) {
        state.update(transform)
    }
}
