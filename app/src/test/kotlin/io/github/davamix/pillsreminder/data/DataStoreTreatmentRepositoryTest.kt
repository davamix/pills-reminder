package io.github.davamix.pillsreminder.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.endTreatment
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.actions.skip
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.model.UnreadableDataException
import io.github.davamix.pillsreminder.core.model.toDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** The store on real files (P3 SPEC §4.3.3–§4.3.5). A "restart" closes the store and opens a new one on the same file. */
class DataStoreTreatmentRepositoryTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "pills.json")
    private val temporaryFile get() = File(dir, "pills.json.tmp")

    /** The scope of the store that is open, cancelled at a restart and at the end of each test. */
    private var job: Job? = null
    private val reasons = mutableListOf<String>()

    private fun open(serializer: Serializer<StoredData>? = null): DataStoreTreatmentRepository {
        check(job == null) { "a store is still open" }
        val scope = CoroutineScope(Dispatchers.IO + Job().also { job = it })
        val onUnreadable: (String) -> Unit = { synchronized(reasons) { reasons += it } }
        return if (serializer == null) {
            DataStoreTreatmentRepository.create(file, scope, onUnreadable)
        } else {
            DataStoreTreatmentRepository(DataStoreFactory.create(serializer, scope = scope) { file }, onUnreadable)
        }
    }

    private suspend fun close() {
        job?.cancelAndJoin()
        job = null
    }

    private suspend fun restart(serializer: Serializer<StoredData>? = null): DataStoreTreatmentRepository {
        close()
        return open(serializer)
    }

    @AfterEach
    fun closeStore() = runTest { close() }

    private suspend fun TreatmentRepository.current(): PillsData =
        when (val stored = data.first()) {
            is StoredData.Readable -> stored.data
            StoredData.Unreadable -> error("the data is Unreadable")
        }

    // Round trip.

    @Test
    fun `with no file the data is empty, and no file is created until the first update`() =
        runTest {
            val repository = open()

            assertEquals(StoredData.Readable(PillsData()), repository.data.first())
            assertFalse(file.exists())

            repository.update { StoreExample.example }
            assertTrue(file.exists())
        }

    @Test
    fun `update returns the new data, and data emits it`() =
        runTest {
            val repository = open()
            repository.data.first()
            val seen =
                async(Dispatchers.Default) {
                    withTimeout(10_000) { repository.data.first { it == StoredData.Readable(StoreExample.full) } }
                }

            assertEquals(StoreExample.full, repository.update { StoreExample.full })
            assertEquals(StoredData.Readable(StoreExample.full), seen.await())
            assertEquals(StoreExample.full, repository.current())
        }

    @Test
    fun `the data and the file are the same after a restart`() =
        runTest {
            open().update { StoreExample.full }

            assertEquals(StoreExample.full, restart().current())
            assertArrayEquals(encodePillsData(StoreExample.full), file.readBytes())
        }

    @Test
    fun `the actions give the same data after a restart`() =
        runTest {
            var repository = open()
            val amoxicillin0800 = TakeKey(StoreExample.amoxicillinId, nov(23, 8))
            val atrovent0800 = TakeKey(StoreExample.atroventId, nov(23, 8))

            repository.saveTreatment(StoreExample.treatment.toDraft(), clockAt(nov(22, 20)))
            assertTrue(repository.markTaken(amoxicillin0800, clockAt(nov(23, 8, 5))))
            assertTrue(repository.skip(atrovent0800, clockAt(nov(23, 8, 6))))
            repository = restart()
            assertEquals(StoreExample.example, repository.current())

            // TR-10: every 12 h from Tuesday on; Monday's takes stay under an earlier schedule.
            val edited =
                StoreExample.treatment.toDraft().let { draft ->
                    draft.copy(
                        medicines =
                            draft.medicines.map {
                                if (it.id ==
                                    StoreExample.amoxicillinId
                                ) {
                                    it.copy(intervalAmount = 12)
                                } else {
                                    it
                                }
                            },
                    )
                }
            repository.saveTreatment(edited, clockAt(nov(24, 10)))
            assertTrue(repository.endTreatment(StoreExample.treatmentId, clockAt(nov(25, 12, 30))))
            val before = repository.current()
            assertTrue(
                before.treatments
                    .single()
                    .medicines
                    .first()
                    .earlierSchedules
                    .isNotEmpty(),
            )
            repository = restart()
            assertEquals(before, repository.current())

            // TR-12: the ended treatment is kept until it is deleted.
            assertTrue(repository.deleteTreatment(StoreExample.treatmentId))
            assertEquals(PillsData(), restart().current())
        }

    @Test
    fun `concurrent updates lose none of one another, also after a restart`() =
        runTest {
            val repository = open()
            withContext(Dispatchers.Default) {
                (1..100)
                    .map { i ->
                        async {
                            repository.update { data ->
                                data.copy(
                                    treatments =
                                        data.treatments + StoreExample.treatment.copy(id = TreatmentId("t$i"), medicines = emptyList()),
                                )
                            }
                        }
                    }.awaitAll()
            }

            assertEquals(100, repository.current().treatments.size)
            assertEquals(
                (1..100).map { "t$it" }.toSet(),
                restart()
                    .current()
                    .treatments
                    .map { it.id.value }
                    .toSet(),
            )
        }

    // Deleting (TR-9).

    @Test
    fun `after deleting a treatment and a restart, the file holds nothing of it`() =
        runTest {
            val repository = open()
            repository.update { StoreExample.full }
            assertTrue(repository.deleteTreatment(StoreExample.endedId))
            restart().current()

            val text = file.readText()
            val gone = listOf("\"ended\"", "\"weekly\"", "\"minutes\"", "Ended example", "Weekly", "Minutes", "Después", "½")
            for (piece in gone) assertFalse(piece in text, "the file still holds $piece")
            assertTrue("Amoxicillin" in text)
        }

    // A file that can't be read (§4.3.5).

    @Test
    fun `a damaged file is Unreadable, refuses every update, and stays byte for byte`() =
        runTest {
            val damaged = encodePillsData(StoreExample.full).let { it.copyOf(it.size / 2) }
            file.writeBytes(damaged)
            val modified = file.lastModified()
            val repository = open()

            assertEquals(StoredData.Unreadable, repository.data.first())
            assertThrows<UnreadableDataException> { repository.update { StoreExample.example } }
            assertThrows<UnreadableDataException> { repository.deleteTreatment(StoreExample.treatmentId) }

            assertArrayEquals(damaged, file.readBytes())
            assertEquals(modified, file.lastModified())
            assertFalse(temporaryFile.exists())
            assertEquals(listOf("parse: JsonDecodingException"), reasons.distinct())
        }

    @Test
    fun `a file that can't be opened is Unreadable`() =
        runTest {
            assertTrue(file.mkdir())
            val repository = open()

            assertEquals(StoredData.Unreadable, repository.data.first())
            assertThrows<IOException> { repository.update { StoreExample.example } }
            assertTrue(file.isDirectory)
            assertTrue(reasons.isNotEmpty() && reasons.all { it.startsWith("read: ") }, reasons.toString())
        }

    // Interrupted writes (§4.3.3).

    /** Writes like the store's serializer, but when [cut] is set, it stops halfway through and throws. */
    private class CuttingSerializer : Serializer<StoredData> {
        private val real = PillsDataSerializer()

        @Volatile var cut = false

        override val defaultValue = real.defaultValue

        override suspend fun readFrom(input: InputStream) = real.readFrom(input)

        override suspend fun writeTo(
            t: StoredData,
            output: OutputStream,
        ) {
            val bytes = encodePillsData((t as StoredData.Readable).data)
            if (!cut) return output.write(bytes)
            output.write(bytes, 0, bytes.size / 2)
            output.flush()
            throw IOException("cut off halfway")
        }
    }

    @Test
    fun `a write cut off halfway leaves the old data, in memory and in the file`() =
        runTest {
            val serializer = CuttingSerializer()
            val repository = open(serializer)
            repository.update { StoreExample.example }
            val saved = file.readBytes()

            serializer.cut = true
            assertThrows<IOException> { repository.update { StoreExample.full } }

            assertEquals(StoreExample.example, repository.current())
            assertArrayEquals(saved, file.readBytes())
            assertFalse(temporaryFile.exists())
            assertEquals(StoreExample.example, restart().current())
        }

    @Test
    fun `a partial temporary file left by a crash is ignored, and the next update succeeds`() =
        runTest {
            open().update { StoreExample.example }
            close()
            temporaryFile.writeBytes(encodePillsData(StoreExample.full).let { it.copyOf(it.size / 2) })

            val repository = open()
            assertEquals(StoreExample.example, repository.current())
            repository.update { StoreExample.full }

            assertEquals(StoreExample.full, restart().current())
            assertFalse(temporaryFile.exists())
        }

    // The write check (P3-D3), and updates that change nothing.

    @Test
    fun `data that wouldn't read back the same is never written`() =
        runTest {
            val repository = open()
            repository.update { StoreExample.example }
            val saved = file.readBytes()
            val withSeconds =
                StoreExample.example.copy(
                    answers =
                        StoreExample.example.answers +
                            (TakeKey(StoreExample.amoxicillinId, nov(23, 16)) to Answer.Taken(nov(23, 16).withSecond(30))),
                )
            val sameIdTwice = StoreExample.full.copy(treatments = StoreExample.full.treatments.map { it.copy(id = TreatmentId("same")) })

            assertThrows<IllegalStateException> { repository.update { withSeconds } }
            assertThrows<IllegalStateException> { repository.update { sameIdTwice } }

            assertEquals(StoreExample.example, repository.current())
            assertArrayEquals(saved, file.readBytes())
            // DataStore deletes its temporary file only after an IOException. The check throws before the first byte,
            // so what it leaves is empty, and the next write replaces it.
            assertEquals(0L, if (temporaryFile.exists()) temporaryFile.length() else 0L)
            repository.update { StoreExample.full }
            assertFalse(temporaryFile.exists())
            assertEquals(StoreExample.full, restart().current())
        }

    @Test
    fun `a refused action and an update that changes nothing leave the file untouched`() =
        runTest {
            val repository = open()
            repository.update { StoreExample.full }
            val saved = file.readBytes()
            val modified = file.lastModified()

            assertFalse(repository.endTreatment(StoreExample.endedId, clockAt(nov(26, 9))), "already ended")
            assertEquals(StoreExample.full, repository.update { it })

            assertArrayEquals(saved, file.readBytes())
            assertEquals(modified, file.lastModified())
        }
}
