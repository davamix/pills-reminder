package io.github.davamix.pillsreminder.ui.common

import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.endTreatment
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.clockAt
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.reminders.FakeRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

/** What a screen says when a change goes wrong (P6 SPEC §4.3.10). */
class AttemptTest {
    private val clock = clockAt(nov(23, 10))
    private val repository = FakeRepository(StoredData.Readable(PillsData(listOf(StoreExample.treatment))))

    private fun stored() = repository.current.treatments.single()

    @Test
    fun `a change that is applied has no problem`() =
        runTest {
            assertNull(attempt { repository.endTreatment(StoreExample.treatmentId, clock) })
            assertEquals(nov(23, 10), stored().endedAt)
        }

    @Test
    fun `a change the rules refuse is not changed`() =
        runTest {
            assertEquals(ChangeProblem.NOT_CHANGED, attempt { repository.deleteTreatment(TreatmentId("none")) })
        }

    @Test
    fun `a change to data that can't be read is unreadable`() =
        runTest {
            val unreadable = InMemoryTreatmentRepository(StoredData.Unreadable)
            assertEquals(ChangeProblem.UNREADABLE, attempt { unreadable.deleteTreatment(StoreExample.treatmentId) })
        }

    @Test
    fun `a write that fails is not saved, and changes nothing`() =
        runTest {
            repository.failure = IOException("disk full")
            assertEquals(ChangeProblem.NOT_SAVED, attempt { repository.endTreatment(StoreExample.treatmentId, clock) })
            assertNull(stored().endedAt)
        }

    @Test
    fun `any other exception is a bug, and goes through`() {
        repository.failure = IllegalStateException("bug")
        assertThrows<IllegalStateException> {
            runTest { attempt { repository.endTreatment(StoreExample.treatmentId, clock) } }
        }
    }
}
