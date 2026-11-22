package io.github.davamix.pillsreminder.ui.treatments

import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import io.github.davamix.pillsreminder.reminders.MutableClock
import io.github.davamix.pillsreminder.ui.TestTicks
import io.github.davamix.pillsreminder.ui.deps
import io.github.davamix.pillsreminder.ui.medicine
import io.github.davamix.pillsreminder.ui.next
import io.github.davamix.pillsreminder.ui.treatment
import io.github.davamix.pillsreminder.ui.vmTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Treatments screen's groups (TR-1, TR-12, spec §4.3 rule 7, P6 SPEC §4.3.3). */
class TreatmentsViewModelTest {
    private val clock = MutableClock(nowAt(nov(23, 10, 30)))
    private val ticks = TestTicks()

    /** On Mon 23 Nov 10:30: two Active, two Upcoming, one ended early and one finished after its last take. */
    private val data =
        PillsData(
            listOf(
                StoreExample.treatment,
                treatment("beta", "beta", medicine("b1", "Ibuprofen", nov(22, 8), days = 3)),
                treatment("later", "Later", medicine("l1", "Atrovent", nov(25, 8), days = 2)),
                treatment(
                    "sooner",
                    "Sooner",
                    medicine("s1", "Atrovent", nov(24, 8), days = 2),
                    medicine("s2", "Amoxicillin", nov(24, 6), days = 2),
                ),
                treatment("ended", "Ended", medicine("e1", "Amoxicillin", nov(20, 8), days = 7), endedAt = nov(23, 9)),
                treatment("done", "Done", medicine("d1", "Amoxicillin", nov(20, 8), days = 1)),
            ),
        )

    private fun viewModel(repository: TreatmentRepository = InMemoryTreatmentRepository(StoredData.Readable(data))) =
        TreatmentsViewModel(deps(repository, clock, ticks))

    private suspend fun TreatmentsViewModel.ready() = state.next() as TreatmentsState.Ready

    @Test
    fun `each treatment is in its group, Active by name, Upcoming soonest first, Finished latest first`() =
        vmTest {
            val state = viewModel().ready()

            assertEquals(listOf("beta", "Example"), state.active.map { it.name })
            assertEquals(listOf("Sooner", "Later"), state.upcoming.map { it.name })
            assertEquals(listOf("Ended", "Done"), state.finished.map { it.name })
            assertEquals(nov(23, 0).toLocalDate(), state.today)
        }

    @Test
    fun `each row has its medicines, when it starts, or when it ended or had its last take`() =
        vmTest {
            val state = viewModel().ready()
            val rows = (state.active + state.upcoming + state.finished).associateBy { it.name }

            assertEquals(listOf("Amoxicillin", "Atrovent"), rows.getValue("Example").medicines)
            assertEquals(nov(24, 6), rows.getValue("Sooner").startsAt)
            assertEquals(nov(23, 9), rows.getValue("Ended").endedAt)
            assertEquals(null, rows.getValue("Ended").lastTake)
            assertEquals(nov(21, 0), rows.getValue("Done").lastTake)
            assertEquals(null, rows.getValue("Done").endedAt)
            assertEquals(null, rows.getValue("Example").startsAt)
        }

    @Test
    fun `an Upcoming treatment moves to Active at its first take, at the next tick`() =
        vmTest {
            clock.at(nov(23, 7, 59))
            val viewModel = viewModel(InMemoryTreatmentRepository(StoredData.Readable(PillsData(listOf(StoreExample.treatment)))))
            assertEquals(listOf("Example"), viewModel.ready().upcoming.map { it.name })

            clock.at(nov(23, 8))
            ticks.tick()

            val state = viewModel.state.next { (it as TreatmentsState.Ready).active.isNotEmpty() } as TreatmentsState.Ready
            assertEquals(listOf("Example"), state.active.map { it.name })
            assertTrue(state.upcoming.isEmpty())
        }

    @Test
    fun `a new treatment shows as soon as it is stored`() =
        vmTest {
            val repository = InMemoryTreatmentRepository(StoredData.Readable(PillsData()))
            val viewModel = viewModel(repository)
            assertTrue(viewModel.ready().isEmpty)

            repository.update { it.copy(treatments = listOf(StoreExample.treatment)) }

            val state = viewModel.state.next { !(it as TreatmentsState.Ready).isEmpty } as TreatmentsState.Ready
            assertEquals(listOf("Example"), state.active.map { it.name })
        }

    @Test
    fun `no treatments is an empty list`() =
        vmTest {
            assertTrue(viewModel(InMemoryTreatmentRepository(StoredData.Readable(PillsData()))).ready().isEmpty)
        }

    @Test
    fun `data that can't be read says so`() =
        vmTest {
            assertEquals(TreatmentsState.Unreadable, viewModel(InMemoryTreatmentRepository(StoredData.Unreadable)).state.next())
        }
}
