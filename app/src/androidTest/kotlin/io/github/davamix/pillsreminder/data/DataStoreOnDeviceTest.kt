package io.github.davamix.pillsreminder.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.UnreadableDataException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * The store on the device's own file system (P3 SPEC §6), on a file in the app's cache: never the app's real file.
 * Test names are sentences without commas (P1-D7).
 */
@RunWith(AndroidJUnit4::class)
class DataStoreOnDeviceTest {
    private val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val dir = File(cache, "store-test-${UUID.randomUUID()}")
    private val file = File(dir, "pills.json")
    private var job: Job? = null

    private fun open(): DataStoreTreatmentRepository {
        check(job == null) { "a store is still open" }
        return DataStoreTreatmentRepository.create(file, CoroutineScope(Dispatchers.IO + Job().also { job = it }))
    }

    private suspend fun restart(): DataStoreTreatmentRepository {
        job?.cancelAndJoin()
        job = null
        return open()
    }

    @After
    fun tearDown() {
        runBlocking { job?.cancelAndJoin() }
        dir.deleteRecursively()
    }

    @Test
    fun `the data reads back the same after a restart of the store`() =
        runBlocking<Unit> {
            val repository = open()
            assertEquals(StoredData.Readable(PillsData()), repository.data.first())
            repository.update { DeviceExample.example }
            val written = file.readBytes()

            assertEquals(StoredData.Readable(DeviceExample.example), restart().data.first())
            assertArrayEquals(written, file.readBytes())
            assertFalse(File(dir, "pills.json.tmp").exists())
        }

    @Test
    fun `a damaged file stays byte for byte and refuses updates`() =
        runBlocking<Unit> {
            dir.mkdirs()
            val damaged = "{\"schemaVersion\":1,\"treatments\":[{\"id\":\"example\",\"name\":\"Exa".encodeToByteArray()
            file.writeBytes(damaged)
            val repository = open()

            assertEquals(StoredData.Unreadable, repository.data.first())
            try {
                repository.update { DeviceExample.example }
                fail("the update was accepted")
            } catch (_: UnreadableDataException) {
            }
            assertArrayEquals(damaged, file.readBytes())
            assertFalse(File(dir, "pills.json.tmp").exists())
        }
}
