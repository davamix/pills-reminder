package io.github.davamix.pillsreminder.data

import androidx.datastore.core.Serializer
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore's serializer for the store (P3 SPEC §4.3.2, §4.3.3). A missing file reads as empty data. A file it can't
 * read becomes [StoredData.Unreadable] instead of a `CorruptionException`, so no corruption handler can ever write over
 * it (P3-D1). [onUnreadable] gets a reason with no treatment content (P3-D6).
 */
class PillsDataSerializer(
    private val onUnreadable: (reason: String) -> Unit = {},
) : Serializer<StoredData> {
    override val defaultValue: StoredData = StoredData.Readable(PillsData())

    override suspend fun readFrom(input: InputStream): StoredData =
        when (val decoded = decodePillsData(input.readBytes())) {
            is Decoded.Readable -> StoredData.Readable(decoded.data)
            is Decoded.Unreadable -> {
                onUnreadable(decoded.reason)
                StoredData.Unreadable
            }
        }

    /**
     * Writes Readable data, after checking that its bytes read back as the same data (P3-D3). Throwing before the first
     * byte makes DataStore keep the old file.
     */
    override suspend fun writeTo(
        t: StoredData,
        output: OutputStream,
    ) {
        val data = checkNotNull((t as? StoredData.Readable)?.data) { "Unreadable data is never written" }
        val bytes = encodePillsData(data)
        check(decodePillsData(bytes) == Decoded.Readable(data)) {
            "The data doesn't read back the same once encoded, so it isn't written"
        }
        output.write(bytes)
    }
}
