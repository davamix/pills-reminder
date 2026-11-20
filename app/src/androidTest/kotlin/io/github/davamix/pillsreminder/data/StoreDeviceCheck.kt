package io.github.davamix.pillsreminder.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.davamix.pillsreminder.PillsReminderApp
import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.actions.skip
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.model.toDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The steps of P3's device checks M1-M6 (SPEC §6, P3-D7), on the app's **real** stored data, through `AppContainer`.
 * It does nothing unless run with `-e storeCheck <step>`, so a whole-suite run never touches the data:
 *
 * - `seed`: refuses unless the data is empty, then saves spec §4.4's example with both 08:00 takes answered.
 * - `verify`: the data is readable and is the example, apart from the notes `churn` writes.
 * - `churn`: rewrites the treatment's notes for up to 60 s, so the process can be killed in the middle of a write.
 * - `remove`: deletes the example treatment.
 *
 * `instrument data.StoreDeviceCheck -e storeCheck seed` (`phone` skill). It logs only counts, under `StoreDeviceCheck`.
 */
@RunWith(AndroidJUnit4::class)
class StoreDeviceCheck {
    private val step: String? = InstrumentationRegistry.getArguments().getString("storeCheck")

    private val repository: TreatmentRepository
        get() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PillsReminderApp
            return app.container.repository
        }

    private suspend fun current(): PillsData =
        when (val stored = repository.data.first()) {
            is StoredData.Readable -> stored.data
            StoredData.Unreadable -> throw AssertionError("The stored data is Unreadable")
        }

    @Test
    fun `store check`() =
        runBlocking<Unit> {
            assumeTrue("Run with -e storeCheck seed or verify or churn or remove", step != null)
            when (step) {
                "seed" -> seed()
                "verify" -> verify()
                "churn" -> churn()
                "remove" -> remove()
                else -> fail("Unknown step $step")
            }
            Log.i(TAG, "step $step passed")
        }

    private suspend fun seed() {
        assertEquals("seed runs only on empty data, and leaves any other data alone", PillsData(), current())
        val example = DeviceExample
        repository.saveTreatment(example.treatment.toDraft(), example.clockAt(example.nov(22, 20)))
        assertTrue(repository.markTaken(TakeKey(example.amoxicillinId, example.nov(23, 8)), example.clockAt(example.nov(23, 8, 5))))
        assertTrue(repository.skip(TakeKey(example.atroventId, example.nov(23, 8)), example.clockAt(example.nov(23, 8, 5))))
        assertEquals(DeviceExample.example, current())
    }

    private suspend fun verify() {
        val data = current()
        val notes = data.treatments.map { it.notes }
        assertTrue("notes $notes", notes.all { it.isEmpty() || it.matches(Regex("churn \\d+")) })
        assertEquals(DeviceExample.example, data.copy(treatments = data.treatments.map { it.copy(notes = "") }))
        Log.i(TAG, "verified: ${data.treatments.size} treatment, ${data.answers.size} answers, notes ${notes.single()}")
    }

    private suspend fun churn() {
        assertEquals(DeviceExample.example, current().let { d -> d.copy(treatments = d.treatments.map { it.copy(notes = "") }) })
        val end = System.currentTimeMillis() + 60_000
        var writes = 0
        while (System.currentTimeMillis() < end) {
            writes++
            repository.update { data -> data.copy(treatments = data.treatments.map { it.copy(notes = "churn $writes") }) }
            if (writes % 100 == 0) Log.i(TAG, "churn: $writes writes")
        }
        Log.i(TAG, "churn: $writes writes, not killed")
    }

    private suspend fun remove() {
        assertTrue("the example is stored", repository.deleteTreatment(DeviceExample.treatmentId))
        assertTrue(current().treatments.none { it.id == DeviceExample.treatmentId })
    }

    private companion object {
        const val TAG = "StoreDeviceCheck"
    }
}
