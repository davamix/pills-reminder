package io.github.davamix.pillsreminder.ui.take

import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import io.github.davamix.pillsreminder.reminders.FakeRepository
import io.github.davamix.pillsreminder.reminders.MutableClock
import io.github.davamix.pillsreminder.ui.TestTicks
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.deps
import io.github.davamix.pillsreminder.ui.next
import io.github.davamix.pillsreminder.ui.vmTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** The Take screen: its state, the answers P2 allows, and what goes wrong (TK-2, TK-4, TK-5, P6 SPEC §4.3.5, §4.3.10). */
class TakeViewModelTest {
    private val clock = MutableClock(nowAt(nov(23, 16, 5)))
    private val ticks = TestTicks()

    private val amox0800 = TakeKey(StoreExample.amoxicillinId, nov(23, 8))
    private val amox1600 = TakeKey(StoreExample.amoxicillinId, nov(23, 16))
    private val amox0000 = TakeKey(StoreExample.amoxicillinId, nov(24, 0))

    /** The §4.4 example with these answers. */
    private fun example(vararg answers: Pair<TakeKey, Answer>) =
        FakeRepository(StoredData.Readable(PillsData(listOf(StoreExample.treatment), answers.toMap())))

    private fun viewModel(
        repository: TreatmentRepository,
        key: TakeKey?,
    ) = TakeViewModel(deps(repository, clock, ticks), key)

    private suspend fun TakeViewModel.ready() = state.next() as TakeScreenState.Ready

    private suspend fun TakeViewModel.readyWhen(predicate: (TakeScreenState.Ready) -> Boolean) =
        state.next { it is TakeScreenState.Ready && predicate(it) } as TakeScreenState.Ready

    @Test
    fun `a Due take shows its medicine, treatment and times, and offers Taken and Skip`() =
        vmTest {
            val state = viewModel(example(amox0800 to Answer.Taken(nov(23, 8, 5))), amox1600).ready()

            assertEquals(
                listOf("Amoxicillin", "1 capsule", "", "Example"),
                listOf(state.medicine, state.dose, state.instructions, state.treatment),
            )
            assertEquals(TakeState.DUE, state.take.state)
            assertEquals(nov(24, 0), state.take.missedAt)
            assertFalse(state.isLastAnswered)
            assertEquals(TakeActions(taken = true, skip = true), state.actions)
        }

    @Test
    fun `the next take before it is due can be marked Taken early, and nothing else`() =
        vmTest {
            clock.at(nov(23, 10, 30))

            val state = viewModel(example(amox0800 to Answer.Taken(nov(23, 8, 5))), amox1600).ready()

            assertEquals(TakeState.PENDING, state.take.state)
            assertEquals(TakeActions(taken = true), state.actions)
        }

    @Test
    fun `a Pending take that isn't the next one offers nothing`() =
        vmTest {
            clock.at(nov(23, 10, 30))

            val state = viewModel(example(amox0800 to Answer.Taken(nov(23, 8, 5))), amox0000).ready()

            assertEquals(TakeState.PENDING, state.take.state)
            assertFalse(state.actions.any)
        }

    @Test
    fun `the last answered take when Taken offers Change time, Skip and Undo`() =
        vmTest {
            clock.at(nov(23, 10, 30))

            val state = viewModel(example(amox0800 to Answer.Taken(nov(23, 8, 5))), amox0800).ready()

            assertTrue(state.isLastAnswered)
            assertEquals(nov(23, 8, 5), state.take.takenAt)
            assertEquals(TakeActions(skip = true, undo = true, changeTime = true), state.actions)
        }

    @Test
    fun `the last answered take when Skipped offers Taken and Undo`() =
        vmTest {
            clock.at(nov(23, 10, 30))

            val state = viewModel(example(amox0800 to Answer.Skipped), amox0800).ready()

            assertEquals(TakeState.SKIPPED, state.take.state)
            assertEquals(TakeActions(taken = true, undo = true), state.actions)
        }

    @Test
    fun `an older answered take can't be changed`() =
        vmTest {
            clock.at(nov(23, 16, 30))
            val repository = example(amox0800 to Answer.Taken(nov(23, 8, 5)), amox1600 to Answer.Taken(nov(23, 16, 2)))

            val state = viewModel(repository, amox0800).ready()

            assertEquals(TakeState.TAKEN, state.take.state)
            assertFalse(state.isLastAnswered)
            assertFalse(state.actions.any)
        }

    @Test
    fun `a Missed take offers nothing`() =
        vmTest {
            val state = viewModel(example(), amox0800).ready()

            assertEquals(TakeState.MISSED, state.take.state)
            assertFalse(state.actions.any)
        }

    @Test
    fun `Taken records now in whole minutes`() =
        vmTest {
            clock.now = nowAt(nov(23, 16, 5)).plusSeconds(45)
            val repository = example(amox0800 to Answer.Taken(nov(23, 8, 5)))
            val viewModel = viewModel(repository, amox1600)
            viewModel.ready()

            viewModel.markTaken()
            advanceUntilIdle()

            assertEquals(Answer.Taken(nov(23, 16, 5)), repository.current.answers[amox1600])
            val state = viewModel.readyWhen { it.take.state == TakeState.TAKEN }
            assertTrue(state.isLastAnswered)
            assertNull(state.problem)
        }

    @Test
    fun `Skip records the take as Skipped`() =
        vmTest {
            val repository = example(amox0800 to Answer.Taken(nov(23, 8, 5)))
            val viewModel = viewModel(repository, amox1600)
            viewModel.ready()

            viewModel.skip()
            advanceUntilIdle()

            assertEquals(Answer.Skipped, repository.current.answers[amox1600])
        }

    @Test
    fun `Change time corrects the time taken`() =
        vmTest {
            val repository = example(amox1600 to Answer.Taken(nov(23, 16, 4)))
            val viewModel = viewModel(repository, amox1600)
            viewModel.ready()

            viewModel.setTakenTime(nov(23, 15, 50))
            advanceUntilIdle()

            assertEquals(Answer.Taken(nov(23, 15, 50)), repository.current.answers[amox1600])
            assertEquals(nov(23, 15, 50), viewModel.readyWhen { it.take.takenAt == nov(23, 15, 50) }.take.takenAt)
        }

    @Test
    fun `a time taken in the future is refused, and the take stays as it was`() =
        vmTest {
            val repository = example(amox1600 to Answer.Taken(nov(23, 16, 4)))
            val viewModel = viewModel(repository, amox1600)
            viewModel.ready()

            viewModel.setTakenTime(nov(23, 16, 10))
            advanceUntilIdle()

            assertEquals(Answer.Taken(nov(23, 16, 4)), repository.current.answers[amox1600])
            assertEquals(ChangeProblem.NOT_CHANGED, viewModel.readyWhen { it.problem != null }.problem)
        }

    @Test
    fun `Undo sets the take back to unanswered, and a take due again is Due`() =
        vmTest {
            val repository = example(amox1600 to Answer.Taken(nov(23, 16, 4)))
            val viewModel = viewModel(repository, amox1600)
            viewModel.ready()

            viewModel.undo()
            advanceUntilIdle()

            assertNull(repository.current.answers[amox1600])
            assertEquals(TakeState.DUE, viewModel.readyWhen { it.take.state == TakeState.DUE }.take.state)
        }

    @Test
    fun `a take turns Due at its minute, at the next tick, and then offers Skip too`() =
        vmTest {
            clock.at(nov(23, 15, 59))
            val viewModel = viewModel(example(amox0800 to Answer.Taken(nov(23, 8, 5))), amox1600)
            assertEquals(TakeActions(taken = true), viewModel.ready().actions)

            clock.at(nov(23, 16))
            ticks.tick()

            val state = viewModel.readyWhen { it.take.state == TakeState.DUE }
            assertEquals(TakeActions(taken = true, skip = true), state.actions)
        }

    @Test
    fun `an answer refused because the take was answered meanwhile says not changed, once`() =
        vmTest {
            val repository = example(amox0800 to Answer.Taken(nov(23, 8, 5)))
            val viewModel = viewModel(repository, amox1600)
            viewModel.ready()
            // Answered from the reminder in the meantime.
            repository.markTaken(amox1600, clock)

            viewModel.markTaken()
            advanceUntilIdle()

            assertEquals(ChangeProblem.NOT_CHANGED, viewModel.readyWhen { it.problem != null }.problem)
            viewModel.problemShown()
            assertNull(viewModel.readyWhen { it.problem == null }.problem)
        }

    @Test
    fun `a take that doesn't exist is missing`() =
        vmTest {
            val repository = example()
            assertEquals(TakeScreenState.Missing, viewModel(repository, TakeKey(MedicineId("gone"), nov(23, 16))).state.next())
            assertEquals(TakeScreenState.Missing, viewModel(repository, TakeKey(StoreExample.amoxicillinId, nov(23, 9))).state.next())
            assertEquals(TakeScreenState.Missing, viewModel(repository, null).state.next())
        }

    @Test
    fun `the route's time is read as it is written, and a time that doesn't parse names no take`() {
        assertEquals(amox1600, TakeViewModel.keyOf("amoxicillin", "2026-11-23T16:00"))
        assertNull(TakeViewModel.keyOf("amoxicillin", "not a time"))
        assertNull(TakeViewModel.keyOf("amoxicillin", "2026-11-23"))
    }

    @Test
    fun `data that can't be read says so, and an answer changes nothing`() =
        vmTest {
            val viewModel = viewModel(InMemoryTreatmentRepository(StoredData.Unreadable), amox1600)
            assertEquals(TakeScreenState.Unreadable, viewModel.state.next())

            viewModel.markTaken()
            advanceUntilIdle()

            assertEquals(TakeScreenState.Unreadable, viewModel.state.next())
        }

    @Test
    fun `a failed write says not saved, and the take stays as it was`() =
        vmTest {
            val repository = example(amox0800 to Answer.Taken(nov(23, 8, 5)))
            val viewModel = viewModel(repository, amox1600)
            viewModel.ready()
            repository.failure = IOException("disk full")

            viewModel.markTaken()
            advanceUntilIdle()

            val state = viewModel.readyWhen { it.problem != null }
            assertEquals(ChangeProblem.NOT_SAVED, state.problem)
            assertEquals(TakeState.DUE, state.take.state)
            assertNull(repository.current.answers[amox1600])
        }
}
