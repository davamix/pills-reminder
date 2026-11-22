package io.github.davamix.pillsreminder.check

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The check's record in its own small file, `files/datastore/reminder-check.json` (P7-D1, plan §2.4). Unlike the
 * treatments' store, a file that can't be read is no loss: it reads as an empty record, and the next write replaces it,
 * so the check only shows again.
 */
class DataStoreCheckStore(
    private val store: DataStore<CheckRecord>,
) : CheckStore {
    override val record: Flow<CheckRecord> =
        store.data.catch { e ->
            if (e !is IOException) throw e
            emit(CheckRecord())
        }

    override suspend fun update(transform: (CheckRecord) -> CheckRecord) {
        store.updateData(transform)
    }

    companion object {
        /** The file's name, in DataStore's folder. */
        const val FILE_NAME = "reminder-check.json"

        /** The store on [file]. Only one may be active per file in the process, while its [scope] is active. */
        fun create(
            file: File,
            scope: CoroutineScope,
        ): DataStoreCheckStore =
            DataStoreCheckStore(DataStoreFactory.create(serializer = CheckRecordSerializer, scope = scope, produceFile = { file }))
    }
}

/** The record as JSON: `{"schemaVersion":1,"firstLaunchDone":true,"confirmed":["AUTOSTART"]}`. */
@Serializable
private data class CheckDocument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val firstLaunchDone: Boolean = false,
    val confirmed: List<String> = emptyList(),
)

private const val SCHEMA_VERSION = 1

/** Every field is written, the defaults too, so the file always says its `schemaVersion`. */
private val json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/** A missing, damaged or unknown record reads as empty; step names it doesn't know are left out. */
internal object CheckRecordSerializer : Serializer<CheckRecord> {
    override val defaultValue: CheckRecord = CheckRecord()

    override suspend fun readFrom(input: InputStream): CheckRecord {
        val document =
            try {
                json.decodeFromString(CheckDocument.serializer(), input.readBytes().decodeToString())
            } catch (e: IllegalArgumentException) {
                // A SerializationException is one: not JSON, or not this document.
                return defaultValue
            }
        if (document.schemaVersion != SCHEMA_VERSION) return defaultValue
        val steps = HyperOsStep.entries.associateBy { it.name }
        return CheckRecord(document.firstLaunchDone, document.confirmed.mapNotNull { steps[it] }.toSet())
    }

    override suspend fun writeTo(
        t: CheckRecord,
        output: OutputStream,
    ) {
        val document = CheckDocument(SCHEMA_VERSION, t.firstLaunchDone, t.confirmed.map { it.name }.sorted())
        output.write(json.encodeToString(CheckDocument.serializer(), document).encodeToByteArray())
    }
}
