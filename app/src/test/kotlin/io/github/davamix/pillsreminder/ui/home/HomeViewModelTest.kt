package io.github.davamix.pillsreminder.ui.home

import io.github.davamix.pillsreminder.check.CheckRecord
import io.github.davamix.pillsreminder.check.CheckStore
import io.github.davamix.pillsreminder.check.HyperOsStep
import io.github.davamix.pillsreminder.check.InMemoryCheckStore
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import io.github.davamix.pillsreminder.reminders.FakeRepository
import io.github.davamix.pillsreminder.reminders.MutableClock
import io.github.davamix.pillsreminder.ui.FakeCheckDevice
import io.github.davamix.pillsreminder.ui.TestTicks
import io.github.davamix.pillsreminder.ui.allSet
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.deps
import io.github.davamix.pillsreminder.ui.medicine
import io.github.davamix.pillsreminder.ui.treatment
import io.github.davamix.pillsreminder.ui.vmTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalDateTime

/**
 * Home's rows, its actions and the check's warning (HM-1–HM-5, TK-2, TK-4, RM-9, P7 SPEC §4.3). Each test keeps Home's
 * state collected, as the screen does, and reads it after `advanceUntilIdle()`, so a state that never comes fails at
 * once instead of waiting for `runTest`'s timeout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val clock = MutableClock(nowAt(nov(23, 10, 30)))
    private val ticks = TestTicks()

    private val amox0800 = TakeKey(StoreExample.amoxicillinId, nov(23, 8))
    private val amox1600 = TakeKey(StoreExample.amoxicillinId, nov(23, 16))
    private val amox0000 = TakeKey(StoreExample.amoxicillinId, nov(24, 0))
    private val atro0800 = TakeKey(StoreExample.atroventId, nov(23, 8))
    private val atro1600 = TakeKey(StoreExample.atroventId, nov(23, 16))
    private val atro0000 = TakeKey(StoreExample.atroventId, nov(24, 0))
    private val atroTue0800 = TakeKey(StoreExample.atroventId, nov(24, 8))

    /** The §4.4 example with both 08:00 takes Taken at 08:05: §4.3.12's start. */
    private val morningTaken =
        PillsData(
            listOf(StoreExample.treatment),
            mapOf(amox0800 to Answer.Taken(nov(23, 8, 5)), atro0800 to Answer.Taken(nov(23, 8, 5))),
        )

    private fun TestScope.home(
        repository: TreatmentRepository = FakeRepository(StoredData.Readable(morningTaken)),
        device: FakeCheckDevice = FakeCheckDevice(),
        store: CheckStore = InMemoryCheckStore(CheckRecord(firstLaunchDone = true)),
    ): HomeViewModel {
        val viewModel = HomeViewModel(deps(repository, clock, ticks, checkDevice = device, checkStore = store))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun TestScope.state(viewModel: HomeViewModel): HomeState {
        advanceUntilIdle()
        return viewModel.state.value
    }

    private fun TestScope.ready(viewModel: HomeViewModel) = state(viewModel).data as HomeData.Ready

    private fun HomeState.row(key: TakeKey) = (data as HomeData.Ready).let { it.due + it.next }.single { it.key == key }

    private fun TestScope.at(
        local: LocalDateTime,
        viewModel: HomeViewModel,
    ): HomeData.Ready {
        clock.at(local)
        ticks.tick()
        return ready(viewModel)
    }

    private fun ahead(
        hours: Int,
        minutes: Int,
        days: Long = 0,
    ) = TimeLeft(past = false, days = days, hours = hours, minutes = minutes)

    private fun ago(
        hours: Int,
        minutes: Int,
    ) = TimeLeft(past = true, days = 0, hours = hours, minutes = minutes)

    /** A row of the §4.4 example: Amoxicillin has 21 takes, Atrovent 15. */
    private fun exampleRow(
        key: TakeKey,
        timeLeft: TimeLeft,
        answered: Int,
        canMarkTaken: Boolean = true,
    ): HomeRow {
        val amoxicillin = key.medicineId == StoreExample.amoxicillinId
        val medicine = if (amoxicillin) StoreExample.amoxicillin else StoreExample.atrovent
        return HomeRow(
            key = key,
            treatmentId = StoreExample.treatmentId,
            treatment = "Example",
            medicine = medicine.name,
            dose = medicine.dose,
            plannedAt = key.plannedAt,
            timeLeft = timeLeft,
            answered = answered,
            count = if (amoxicillin) 21L else 15L,
            canMarkTaken = canMarkTaken,
        )
    }

    // Rows

    @Test
    fun `the example step by step follows the clock and the answers`() =
        vmTest {
            val repository = FakeRepository(StoredData.Readable(morningTaken))
            val home = home(repository)

            // 10:30: both next takes at 16:00, ties by the medicine's place, each can be marked early.
            var data = ready(home)
            assertEquals(emptyList<HomeRow>(), data.due)
            assertEquals(listOf(exampleRow(amox1600, ahead(5, 30), 1), exampleRow(atro1600, ahead(5, 30), 1)), data.next)
            assertEquals(nov(23, 0).toLocalDate(), data.today)

            // 16:00: both move to Due at that tick.
            data = at(nov(23, 16), home)
            assertEquals(listOf(exampleRow(amox1600, ago(0, 0), 1), exampleRow(atro1600, ago(0, 0), 1)), data.due)
            assertEquals(emptyList<HomeRow>(), data.next)

            // 16:01: Taken on Atrovent.
            at(nov(23, 16, 1), home)
            home.markTaken(atro1600)
            data = ready(home)
            assertEquals(Answer.Taken(nov(23, 16, 1)), repository.current.answers[atro1600])
            assertEquals(listOf(exampleRow(amox1600, ago(0, 1), 1)), data.due)
            assertEquals(listOf(exampleRow(atro0000, ahead(7, 59), 2)), data.next)

            // 16:05: §2.1's sketch.
            data = at(nov(23, 16, 5), home)
            assertEquals(listOf(exampleRow(amox1600, ago(0, 5), 1)), data.due)
            assertEquals(listOf(exampleRow(atro0000, ahead(7, 55), 2)), data.next)

            // 16:10: Taken early on Atrovent; its following take can't be marked early too (TK-4).
            at(nov(23, 16, 10), home)
            home.markTaken(atro0000)
            data = ready(home)
            assertEquals(Answer.Taken(nov(23, 16, 10)), repository.current.answers[atro0000])
            assertEquals(listOf(exampleRow(amox1600, ago(0, 10), 1)), data.due)
            assertEquals(listOf(exampleRow(atroTue0800, ahead(15, 50), 3, canMarkTaken = false)), data.next)

            // Tue 00:00: Amoxicillin's 16:00 take is Missed and its 00:00 take Due; Atrovent's 08:00 take can be marked early again.
            data = at(nov(24, 0), home)
            assertEquals(listOf(exampleRow(amox0000, ago(0, 0), 1)), data.due)
            assertEquals(listOf(exampleRow(atroTue0800, ahead(8, 0), 3)), data.next)
            assertEquals(nov(24, 0).toLocalDate(), data.today)
            assertNull(state(home).problem)
        }

    @Test
    fun `Due and Next are by planned time, then by treatment name in any case, then by the medicine's place`() =
        vmTest {
            val taken = { id: String, at: LocalDateTime -> TakeKey(MedicineId(id), at) to Answer.Taken(at) }
            val data =
                PillsData(
                    listOf(
                        treatment("beta", "Beta", medicine("b", "Ibuprofen", nov(23, 8), days = 3)),
                        treatment(
                            "alpha",
                            "alpha",
                            medicine("z", "Zinc", nov(23, 8), days = 3),
                            medicine("a", "Aspirin", nov(23, 8), days = 3),
                        ),
                        treatment("early", "Early", medicine("e", "Paracetamol", nov(23, 6), days = 3)),
                        treatment("zeta", "Zeta", medicine("d9", "Vitamin C", nov(23, 9), days = 3, every = 24)),
                        treatment("aardvark", "Aardvark", medicine("d10", "Vitamin D", nov(23, 10), days = 3, every = 24)),
                    ),
                    mapOf(taken("b", nov(23, 8)), taken("z", nov(23, 8)), taken("a", nov(23, 8)), taken("e", nov(23, 6))),
                )

            val home = ready(home(InMemoryTreatmentRepository(StoredData.Readable(data))))

            assertEquals(listOf("d9", "d10"), home.due.map { it.key.medicineId.value })
            assertEquals(listOf("e", "z", "a", "b"), home.next.map { it.key.medicineId.value })
        }

    @Test
    fun `before it starts a treatment is Upcoming with its first take, soonest first, and has no rows`() =
        vmTest {
            clock.at(nov(22, 10, 30))
            val data =
                PillsData(listOf(treatment("later", "Later", medicine("l", "Atrovent", nov(25, 8), days = 2)), StoreExample.treatment))

            val home = ready(home(InMemoryTreatmentRepository(StoredData.Readable(data))))

            assertEquals(
                listOf(
                    UpcomingRow(StoreExample.treatmentId, "Example", nov(23, 8), ahead(21, 30)),
                    UpcomingRow(TreatmentId("later"), "Later", nov(25, 8), ahead(21, 30, days = 2)),
                ),
                home.upcoming,
            )
            assertEquals(emptyList<HomeRow>(), home.due + home.next)
        }

    @Test
    fun `a medicine with no end has no count, and one with nothing left has no row`() =
        vmTest {
            val long = MedicineId("long")
            val data =
                PillsData(
                    listOf(
                        treatment(
                            "mixed",
                            "Mixed",
                            // Its three takes, the last at 00:00, are all Missed by 08:00.
                            medicine("short", "Short", nov(22, 8), days = 1),
                            medicine("long", "Long", nov(20, 9), days = null, every = 24),
                        ),
                    ),
                    mapOf(TakeKey(long, nov(21, 9)) to Answer.Skipped, TakeKey(long, nov(22, 9)) to Answer.Taken(nov(22, 9))),
                )

            val home = ready(home(InMemoryTreatmentRepository(StoredData.Readable(data))))

            val row =
                HomeRow(
                    key = TakeKey(long, nov(23, 9)),
                    treatmentId = TreatmentId("mixed"),
                    treatment = "Mixed",
                    medicine = "Long",
                    dose = "1 tablet",
                    plannedAt = nov(23, 9),
                    timeLeft = ago(1, 30),
                    answered = 2,
                    count = null,
                    canMarkTaken = true,
                )
            assertEquals(listOf(row), home.due)
            assertEquals(emptyList<HomeRow>(), home.next)
        }

    @Test
    fun `Finished treatments aren't on Home, so with only those it is empty`() =
        vmTest {
            val data =
                PillsData(
                    listOf(
                        treatment("ended", "Ended", medicine("e1", "Amoxicillin", nov(20, 8), days = 7), endedAt = nov(23, 9)),
                        treatment("done", "Done", medicine("d1", "Amoxicillin", nov(20, 8), days = 1)),
                    ),
                )

            val home = ready(home(InMemoryTreatmentRepository(StoredData.Readable(data))))

            assertTrue(home.isEmpty)
            assertEquals(nov(23, 0).toLocalDate(), home.today)
        }

    @Test
    fun `unreadable data shows as unreadable, with the check's warning`() =
        vmTest {
            val state =
                state(
                    home(
                        InMemoryTreatmentRepository(StoredData.Unreadable),
                        device = FakeCheckDevice(allSet.copy(batteryOptimised = true)),
                    ),
                )

            assertEquals(HomeData.Unreadable, state.data)
            assertEquals(1, state.toCheck)
        }

    @Test
    fun `until the data is read there are no sections, but the warning shows`() =
        vmTest {
            val state = state(home(NeverReadRepository(), device = FakeCheckDevice(allSet.copy(batteryOptimised = true))))

            assertNull(state.data)
            assertEquals(1, state.toCheck)
        }

    // Actions

    @Test
    fun `Taken records now in whole minutes, and the row shows the medicine's next take`() =
        vmTest {
            clock.now = nowAt(nov(23, 16, 5)).plusSeconds(30)
            val repository = FakeRepository(StoredData.Readable(morningTaken))
            val home = home(repository)
            assertEquals(listOf(amox1600, atro1600), ready(home).due.map { it.key })

            home.markTaken(amox1600)
            val data = ready(home)

            assertEquals(Answer.Taken(nov(23, 16, 5)), repository.current.answers[amox1600])
            assertEquals(listOf(atro1600), data.due.map { it.key })
            assertEquals(listOf(exampleRow(amox0000, ahead(7, 55), 2)), data.next)
            assertNull(state(home).problem)
        }

    @Test
    fun `a take answered meanwhile is refused with Not changed, and the rows show its new state`() =
        vmTest {
            clock.at(nov(23, 16, 5))
            val repository = FakeRepository(StoredData.Readable(morningTaken))
            val home = home(repository)
            ready(home)

            repository.markTaken(amox1600, clock)
            home.markTaken(amox1600)
            val state = state(home)

            assertEquals(ChangeProblem.NOT_CHANGED, state.problem)
            assertEquals(listOf(atro1600), (state.data as HomeData.Ready).due.map { it.key })
            home.problemShown()
            assertNull(state(home).problem)
        }

    @Test
    fun `a failed write gives Not saved, and Taken is offered again`() =
        vmTest {
            clock.at(nov(23, 16, 5))
            val repository = FakeRepository(StoredData.Readable(morningTaken)).apply { failure = IOException("full") }
            val home = home(repository)

            home.markTaken(amox1600)
            val state = state(home)

            assertEquals(ChangeProblem.NOT_SAVED, state.problem)
            assertTrue(state.row(amox1600).canMarkTaken)
        }

    @Test
    fun `Taken on unreadable data gives its problem`() =
        vmTest {
            val home = home(InMemoryTreatmentRepository(StoredData.Unreadable))

            home.markTaken(amox1600)

            assertEquals(ChangeProblem.UNREADABLE, state(home).problem)
        }

    @Test
    fun `a double tap marks once, and Taken stays off until the rows show the change`() =
        vmTest {
            clock.at(nov(23, 16, 5))
            val repository = LaggingRepository(morningTaken)
            val home = home(repository)
            assertTrue(state(home).row(amox1600).canMarkTaken)

            home.markTaken(amox1600)
            home.markTaken(amox1600)
            var state = state(home)
            assertEquals(1, repository.writes)
            assertNull(state.problem)
            assertFalse(state.row(amox1600).canMarkTaken)

            // Applied, but the rows still show the data from before: a third tap is ignored too.
            home.markTaken(amox1600)
            state = state(home)
            assertEquals(1, repository.writes)
            assertNull(state.problem)

            repository.show()
            assertEquals(listOf(atro1600), ready(home).due.map { it.key })

            // The answer cleared elsewhere, the data back as it was: Taken is offered again.
            repository.set(morningTaken)
            assertTrue(state(home).row(amox1600).canMarkTaken)
        }

    // The check

    @Test
    fun `the warning counts what is missing and HyperOS's steps to confirm, and follows the device when Home is resumed`() =
        vmTest {
            val device = FakeCheckDevice(allSet.copy(batteryOptimised = true, hyperOs = true))
            val store = InMemoryCheckStore(CheckRecord(firstLaunchDone = true, confirmed = setOf(HyperOsStep.AUTOSTART)))
            val home = home(device = device, store = store)

            assertEquals(3, state(home).toCheck)
            assertEquals(1, device.reads)

            device.checks = allSet
            home.refreshCheck()

            assertEquals(0, state(home).toCheck)
            assertEquals(2, device.reads)
        }

    @Test
    fun `a step confirmed in the record takes its line off the warning`() =
        vmTest {
            val store = InMemoryCheckStore(CheckRecord(firstLaunchDone = true))
            val home = home(device = FakeCheckDevice(allSet.copy(hyperOs = true)), store = store)
            assertEquals(3, state(home).toCheck)

            store.update { it.copy(confirmed = setOf(HyperOsStep.FLOATING_NOTIFICATIONS, HyperOsStep.LOCK_SCREEN_NOTIFICATIONS)) }

            assertEquals(1, state(home).toCheck)
        }

    @Test
    fun `at the first launch Home opens the check once, and not at the next launch`() =
        vmTest {
            val store = InMemoryCheckStore()
            val home = home(store = store)
            assertTrue(state(home).openCheck)

            home.checkOpened()

            assertFalse(state(home).openCheck)
            assertEquals(CheckRecord(firstLaunchDone = true), store.record.value)

            val next = state(home(device = FakeCheckDevice(allSet.copy(batteryOptimised = true)), store = store))
            assertEquals(1, next.toCheck)
            assertFalse(next.openCheck)
        }

    @Test
    fun `once opened the check doesn't open again, even when the record can't be written`() =
        vmTest {
            val store = FailingCheckStore()
            val home = home(store = store)
            assertTrue(state(home).openCheck)

            home.checkOpened()

            assertFalse(state(home).openCheck)
            assertFalse(store.record.value.firstLaunchDone)
        }

    @Test
    fun `until the check's record is read there is no warning and no first launch`() =
        vmTest {
            val state =
                state(home(device = FakeCheckDevice(allSet.copy(batteryOptimised = true, hyperOs = true)), store = NeverReadCheckStore()))

            assertTrue(state.data is HomeData.Ready)
            assertEquals(0, state.toCheck)
            assertFalse(state.openCheck)
        }

    /** A store whose data shows a change only when the test says so, as a slow store's flow could. */
    private class LaggingRepository(
        initial: PillsData,
    ) : TreatmentRepository {
        private var current = initial
        private val shown = MutableStateFlow<StoredData>(StoredData.Readable(initial))
        var writes = 0
            private set

        override val data: Flow<StoredData> = shown

        override suspend fun update(transform: (PillsData) -> PillsData): PillsData {
            val after = transform(current)
            if (after != current) writes++
            current = after
            return after
        }

        /** The data shows the changes made so far. */
        fun show() {
            shown.value = StoredData.Readable(current)
        }

        /** The data is changed elsewhere, and shown. */
        fun set(data: PillsData) {
            current = data
            show()
        }
    }

    /** Data that is never read, as while the store is still opening its file. */
    private class NeverReadRepository : TreatmentRepository {
        override val data: Flow<StoredData> = MutableSharedFlow()

        override suspend fun update(transform: (PillsData) -> PillsData): PillsData = error("The data isn't read yet")
    }

    /** A check's record that can't be written: each update throws, as a full disk would. */
    private class FailingCheckStore : CheckStore {
        override val record = MutableStateFlow(CheckRecord())

        override suspend fun update(transform: (CheckRecord) -> CheckRecord): Unit = throw IOException("full")
    }

    /** A check's record that is never read. */
    private class NeverReadCheckStore : CheckStore {
        override val record: Flow<CheckRecord> = MutableSharedFlow()

        override suspend fun update(transform: (CheckRecord) -> CheckRecord) = Unit
    }
}
