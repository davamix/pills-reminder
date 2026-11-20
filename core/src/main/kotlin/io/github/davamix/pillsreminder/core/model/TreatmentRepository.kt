package io.github.davamix.pillsreminder.core.model

import kotlinx.coroutines.flow.Flow

/** What the repository holds: the data, or the fact that the stored file can't be read. */
sealed interface StoredData {
    data class Readable(
        val data: PillsData,
    ) : StoredData

    /** The stored file exists but can't be read (NF-4, P3). It is kept as it is: nothing may write over it. */
    data object Unreadable : StoredData
}

/**
 * Where the treatments and answers are kept: P3's DataStore store, or [InMemoryTreatmentRepository] until it is merged.
 * Every rule lives in pure functions on [PillsData], run inside [update] (P2-D4), so all implementations behave the
 * same. The helpers in `core.actions` (`saveTreatment`, `markTaken`, …) do that for each action.
 */
interface TreatmentRepository {
    /** The stored data, then every change to it. */
    val data: Flow<StoredData>

    /**
     * Replaces the data with `transform(current)`, atomically, and returns the new data. [transform] must be pure: no
     * side effects, because an implementation may run it again.
     *
     * @throws UnreadableDataException when the data is [StoredData.Unreadable]; nothing changes then. P3's store may
     *   also throw an `IOException` when the write fails.
     */
    suspend fun update(transform: (PillsData) -> PillsData): PillsData
}

/** The stored data can't be read, so it can't be changed either (NF-4). */
class UnreadableDataException : Exception("The stored data can't be read, so it isn't changed")
