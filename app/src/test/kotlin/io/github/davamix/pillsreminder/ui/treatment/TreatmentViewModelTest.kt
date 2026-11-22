package io.github.davamix.pillsreminder.ui.treatment

import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.TreatmentStatus
import io.github.davamix.pillsreminder.core.schedule.take
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import io.github.davamix.pillsreminder.reminders.FakeRepository
import io.github.davamix.pillsreminder.reminders.MutableClock
import io.github.davamix.pillsreminder.ui.TestTicks
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.deps
import io.github.davamix.pillsreminder.ui.medicine
import io.github.davamix.pillsreminder.ui.next
import io.github.davamix.pillsreminder.ui.treatment
import io.github.davamix.pillsreminder.ui.vmTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalDateTime

/** One treatment, End early and Delete (TR-9, TR-11, TK-5, SC-5, P6 SPEC §4.3.4, §4.3.10). */
class TreatmentViewModelTest {
    private val clock = MutableClock(nowAt(nov(23, 10, 30)))
    private val ticks = TestTicks()

    private val amoxicillin = StoreExample.amoxicillinId
    private val atrovent = StoreExample.atroventId

    private fun key(
        medicine: MedicineId,
        at: LocalDateTime,
    ) = TakeKey(medicine, at)

    /** The §4.4 example with these answers. */
    private fun example(vararg answers: Pair<TakeKey, Answer>) = PillsData(listOf(StoreExample.treatment), answers.toMap())

    private fun readable(data: PillsData) = FakeRepository(StoredData.Readable(data))

    private fun viewModel(
        repository: TreatmentRepository,
        id: TreatmentId = StoreExample.treatmentId,
    ) = TreatmentViewModel(deps(repository, clock, ticks), id)

    private suspend fun TreatmentViewModel.ready() = state.next() as TreatmentState.Ready

    @Test
    fun `the example on Monday at 10h30 shows each medicine's progress, last take, next take and last answered take`() =
        vmTest {
            val data =
                example(
                    key(amoxicillin, nov(23, 8)) to Answer.Taken(nov(23, 8, 5)),
                    key(atrovent, nov(23, 8)) to Answer.Taken(nov(23, 8, 5)),
                )

            val state = viewModel(readable(data)).ready()

            assertEquals(TreatmentStatus.ACTIVE, state.status)
            assertTrue(state.canEdit)
            assertTrue(state.canEnd)
            assertNull(state.startsAt)
            assertNull(state.endedAt)
            assertNull(state.lastTake)
            val (amox, atro) = state.medicines
            assertEquals(listOf(1, 21L, nov(30, 0)), listOf(amox.answered, amox.count, amox.last))
            assertEquals(listOf(1, 15L, nov(28, 0)), listOf(atro.answered, atro.count, atro.last))
            assertEquals(key(amoxicillin, nov(23, 16)), amox.next?.key)
            assertEquals(TakeState.PENDING, amox.next?.state)
            assertEquals(key(amoxicillin, nov(23, 8)), amox.lastAnswered?.key)
            assertEquals(nov(23, 8, 5), amox.lastAnswered?.takenAt)
            assertEquals("Amoxicillin", amox.name)
            assertEquals("1 capsule", amox.dose)
        }

    @Test
    fun `a medicine with no end has no count and no last take`() =
        vmTest {
            val weekly = medicine("v", "Vitamin D", nov(16, 9), days = null, every = 1, unit = IntervalUnit.WEEKS)
            val data = PillsData(listOf(treatment("t", "Vitamins", weekly)))

            val card = viewModel(readable(data), TreatmentId("t")).ready().medicines.single()

            assertNull(card.count)
            assertNull(card.last)
            assertEquals(key(weekly.id, nov(23, 9)), card.next?.key)
            assertEquals(TakeState.DUE, card.next?.state)
        }

    @Test
    fun `an Upcoming treatment says when it starts, and can be ended`() =
        vmTest {
            clock.at(nov(22, 10))

            val state = viewModel(readable(example())).ready()

            assertEquals(TreatmentStatus.UPCOMING, state.status)
            assertEquals(nov(23, 8), state.startsAt)
            assertTrue(state.canEnd)
            assertNull(state.medicines.first().lastAnswered)
        }

    @Test
    fun `the next take turns Due at its minute, at the next tick`() =
        vmTest {
            clock.at(nov(23, 15, 59))
            val viewModel = viewModel(readable(example(key(amoxicillin, nov(23, 8)) to Answer.Skipped)))
            assertEquals(
                TakeState.PENDING,
                viewModel
                    .ready()
                    .medicines
                    .first()
                    .next
                    ?.state,
            )

            clock.at(nov(23, 16))
            ticks.tick()

            val state =
                viewModel.state.next {
                    (it as TreatmentState.Ready)
                        .medicines
                        .first()
                        .next
                        ?.state == TakeState.DUE
                }
            assertEquals(
                key(amoxicillin, nov(23, 16)),
                (state as TreatmentState.Ready)
                    .medicines
                    .first()
                    .next
                    ?.key,
            )
        }

    @Test
    fun `End early ends it now, the Due take becomes Missed, and a take marked early keeps its answer`() =
        vmTest {
            val repository =
                readable(
                    example(
                        key(amoxicillin, nov(23, 8)) to Answer.Taken(nov(23, 8, 5)),
                        key(amoxicillin, nov(23, 16)) to Answer.Taken(nov(23, 10, 20)),
                    ),
                )
            val viewModel = viewModel(repository)
            viewModel.ready()

            viewModel.end()
            advanceUntilIdle()

            val state = viewModel.state.next { (it as TreatmentState.Ready).endedAt != null } as TreatmentState.Ready
            assertEquals(TreatmentStatus.FINISHED, state.status)
            assertEquals(nov(23, 10, 30), state.endedAt)
            assertFalse(state.canEnd)
            assertFalse(state.canEdit)
            assertNull(state.problem)
            val now = nowAt(nov(23, 10, 30))
            assertEquals(TakeState.MISSED, repository.current.take(key(atrovent, nov(23, 8)), now)?.state)
            assertEquals(
                key(amoxicillin, nov(23, 16)),
                state.medicines
                    .first()
                    .lastAnswered
                    ?.key,
            )
            assertEquals(
                TakeState.TAKEN,
                state.medicines
                    .first()
                    .lastAnswered
                    ?.state,
            )
            assertTrue(state.medicines.all { it.next == null })
        }

    @Test
    fun `End on a treatment already ended changes nothing and says nothing`() =
        vmTest {
            val ended = StoreExample.treatment.copy(endedAt = nov(23, 9))
            val repository = readable(PillsData(listOf(ended)))
            val viewModel = viewModel(repository)
            assertFalse(viewModel.ready().canEnd)

            viewModel.end()
            advanceUntilIdle()

            assertEquals(0, repository.writes)
            val state = viewModel.ready()
            assertNull(state.problem)
            assertEquals(nov(23, 9), state.endedAt)
        }

    @Test
    fun `a Finished treatment that wasn't ended can still be edited, but not ended`() =
        vmTest {
            clock.at(LocalDateTime.of(2026, 12, 1, 9, 0))

            val state = viewModel(readable(example())).ready()

            assertEquals(TreatmentStatus.FINISHED, state.status)
            assertTrue(state.canEdit)
            assertFalse(state.canEnd)
            assertEquals(nov(30, 0), state.lastTake)
            assertNull(state.endedAt)
        }

    @Test
    fun `Delete removes the treatment and its answers, and the screen leaves`() =
        vmTest {
            val repository = readable(example(key(amoxicillin, nov(23, 8)) to Answer.Skipped))
            val viewModel = viewModel(repository)
            viewModel.ready()

            viewModel.delete()
            advanceUntilIdle()

            assertTrue(viewModel.deleted.value)
            assertTrue(repository.current.treatments.isEmpty())
            assertTrue(repository.current.answers.isEmpty())
            // While the screen leaves it shows nothing, not "This treatment no longer exists".
            assertNull(viewModel.state.first { it !is TreatmentState.Ready })
        }

    @Test
    fun `Delete of a treatment already gone leaves too`() =
        vmTest {
            val viewModel = viewModel(readable(PillsData()))

            viewModel.delete()
            advanceUntilIdle()

            assertTrue(viewModel.deleted.value)
        }

    @Test
    fun `a treatment that isn't there is missing`() =
        vmTest {
            assertEquals(TreatmentState.Missing, viewModel(readable(example()), TreatmentId("gone")).state.next())
        }

    @Test
    fun `data that can't be read says so, and Delete doesn't leave`() =
        vmTest {
            val viewModel = viewModel(InMemoryTreatmentRepository(StoredData.Unreadable))
            assertEquals(TreatmentState.Unreadable, viewModel.state.next())

            viewModel.delete()
            advanceUntilIdle()

            assertFalse(viewModel.deleted.value)
        }

    @Test
    fun `a failed write says not saved once, and changes nothing`() =
        vmTest {
            val repository = readable(example())
            val viewModel = viewModel(repository)
            viewModel.ready()
            repository.failure = IOException("disk full")

            viewModel.end()
            advanceUntilIdle()
            val failed = viewModel.state.next { (it as TreatmentState.Ready).problem != null } as TreatmentState.Ready
            assertEquals(ChangeProblem.NOT_SAVED, failed.problem)
            assertNull(failed.endedAt)

            viewModel.problemShown()
            viewModel.state.next { (it as TreatmentState.Ready).problem == null }

            viewModel.delete()
            advanceUntilIdle()
            assertFalse(viewModel.deleted.value)
            assertEquals(1, repository.current.treatments.size)
        }
}
