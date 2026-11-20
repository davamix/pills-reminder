package io.github.davamix.pillsreminder.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.model.UnreadableDataException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.File
import java.io.IOException

/**
 * P2's repository on a DataStore of [StoredData] (P3 SPEC §4.3.4). It behaves as `InMemoryTreatmentRepository` does,
 * and also keeps the data in a file: written atomically, and never written over once it can't be read (NF-4).
 */
class DataStoreTreatmentRepository(
    private val store: DataStore<StoredData>,
    private val onUnreadable: (reason: String) -> Unit = {},
) : TreatmentRepository {
    /** Readable or Unreadable, then every change. A file that can't be opened gives Unreadable, and the flow ends. */
    override val data: Flow<StoredData> =
        store.data.catch { e ->
            if (e !is IOException) throw e
            onUnreadable("read: ${e::class.simpleName}")
            emit(StoredData.Unreadable)
        }

    /**
     * Runs [transform] on the latest data, one update at a time, and writes the result if it changed.
     *
     * @throws UnreadableDataException when the data is Unreadable; nothing is written then.
     * @throws IOException when the file can't be read or written; the data stays as it was.
     * @throws IllegalStateException when the new data wouldn't read back the same (P3-D3); nothing is written.
     */
    override suspend fun update(transform: (PillsData) -> PillsData): PillsData {
        val updated =
            store.updateData { current ->
                val readable = current as? StoredData.Readable ?: throw UnreadableDataException()
                StoredData.Readable(transform(readable.data))
            }
        return (updated as StoredData.Readable).data
    }

    companion object {
        /**
         * The store on [file]. Only one may be active per file in the process: DataStore refuses a second while the
         * first one's [scope] is active. [scope] lives as long as the store.
         */
        fun create(
            file: File,
            scope: CoroutineScope,
            onUnreadable: (reason: String) -> Unit = {},
        ): DataStoreTreatmentRepository =
            DataStoreTreatmentRepository(
                DataStoreFactory.create(serializer = PillsDataSerializer(onUnreadable), scope = scope, produceFile = { file }),
                onUnreadable,
            )
    }
}
