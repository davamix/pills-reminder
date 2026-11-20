package io.github.davamix.pillsreminder.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [TreatmentRepository] in memory, for tests and for the app until P3's store is merged. Start it
 * [StoredData.Unreadable] to try how a screen handles a damaged file.
 */
class InMemoryTreatmentRepository(
    initial: StoredData = StoredData.Readable(PillsData()),
) : TreatmentRepository {
    private val state = MutableStateFlow(initial)
    private val lock = Mutex()

    override val data: StateFlow<StoredData> = state.asStateFlow()

    override suspend fun update(transform: (PillsData) -> PillsData): PillsData =
        lock.withLock {
            val current = state.value as? StoredData.Readable ?: throw UnreadableDataException()
            transform(current.data).also { state.value = StoredData.Readable(it) }
        }
}
