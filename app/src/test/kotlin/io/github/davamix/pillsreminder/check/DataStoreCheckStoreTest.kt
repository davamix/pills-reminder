package io.github.davamix.pillsreminder.check

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** The check's own record in its file (P7-D1). A "restart" closes the store and opens a new one on the same file. */
class DataStoreCheckStoreTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, DataStoreCheckStore.FILE_NAME)

    /** The scope of the store that is open, cancelled at a restart and at the end of each test. */
    private var job: Job? = null

    private fun open(): DataStoreCheckStore {
        check(job == null) { "a store is still open" }
        return DataStoreCheckStore.create(file, CoroutineScope(Dispatchers.IO + Job().also { job = it }))
    }

    private suspend fun close() {
        job?.cancelAndJoin()
        job = null
    }

    private suspend fun restart(): DataStoreCheckStore {
        close()
        return open()
    }

    @AfterEach
    fun closeStore() = runTest { close() }

    private suspend fun write(text: String) = withContext(Dispatchers.IO) { file.writeText(text) }

    @Test
    fun `with no file the record is empty`() =
        runTest {
            assertEquals(CheckRecord(), open().record.first())
            assertFalse(file.exists())
        }

    @Test
    fun `an update is kept, and read again after a restart`() =
        runTest {
            val store = open()
            store.update { it.copy(firstLaunchDone = true) }
            store.update { it.copy(confirmed = it.confirmed + HyperOsStep.FLOATING_NOTIFICATIONS) }

            val expected = CheckRecord(firstLaunchDone = true, confirmed = setOf(HyperOsStep.FLOATING_NOTIFICATIONS))
            assertEquals(expected, store.record.first())
            assertEquals(expected, restart().record.first())
        }

    @Test
    fun `the file holds the schema version, the first launch and the steps by name`() =
        runTest {
            open().update {
                CheckRecord(firstLaunchDone = true, confirmed = setOf(HyperOsStep.LOCK_SCREEN_NOTIFICATIONS, HyperOsStep.AUTOSTART))
            }
            close()

            val expected =
                buildJsonObject {
                    put("schemaVersion", JsonPrimitive(1))
                    put("firstLaunchDone", JsonPrimitive(true))
                    put("confirmed", JsonArray(listOf(JsonPrimitive("AUTOSTART"), JsonPrimitive("LOCK_SCREEN_NOTIFICATIONS"))))
                }
            assertEquals(expected, Json.parseToJsonElement(file.readText()))
        }

    @Test
    fun `a damaged file reads as an empty record, and the next write replaces it`() =
        runTest {
            write("{\"schemaVersion\":1,\"firstLaunchDone\":tru")
            val store = open()

            assertEquals(CheckRecord(), store.record.first())
            store.update { it.copy(confirmed = setOf(HyperOsStep.AUTOSTART)) }
            assertEquals(CheckRecord(confirmed = setOf(HyperOsStep.AUTOSTART)), restart().record.first())
        }

    @Test
    fun `a file that isn't the record, or of another version, reads as empty`() =
        runTest {
            write("[1, 2, 3]")
            assertEquals(CheckRecord(), open().record.first())

            close()
            write("{\"schemaVersion\":2,\"firstLaunchDone\":true,\"confirmed\":[\"AUTOSTART\"]}")
            assertEquals(CheckRecord(), open().record.first())
        }

    @Test
    fun `a step name it doesn't know is left out, and the rest kept`() =
        runTest {
            write("{\"schemaVersion\":1,\"firstLaunchDone\":true,\"confirmed\":[\"AUTOSTART\",\"BATTERY_SAVER\"],\"later\":0}")

            assertEquals(CheckRecord(firstLaunchDone = true, confirmed = setOf(HyperOsStep.AUTOSTART)), open().record.first())
        }

    @Test
    fun `a file that can't be opened reads as an empty record, and a write throws an IOException`() =
        runTest {
            withContext(Dispatchers.IO) { File(file, "inside").mkdirs() }
            val store = open()

            assertEquals(CheckRecord(), store.record.first())
            assertThrows<IOException> { store.update { it.copy(firstLaunchDone = true) } }
        }

    @Test
    fun `updates made together each apply to the latest record`() =
        runTest {
            val store = open()
            withContext(Dispatchers.IO) {
                HyperOsStep.entries.map { step -> async { store.update { it.copy(confirmed = it.confirmed + step) } } }.awaitAll()
            }

            assertEquals(HyperOsStep.entries.toSet(), restart().record.first().confirmed)
        }
}
