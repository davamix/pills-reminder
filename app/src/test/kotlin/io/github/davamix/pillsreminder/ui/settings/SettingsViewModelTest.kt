package io.github.davamix.pillsreminder.ui.settings

import androidx.lifecycle.SavedStateHandle
import io.github.davamix.pillsreminder.check.CheckItem
import io.github.davamix.pillsreminder.check.CheckRecord
import io.github.davamix.pillsreminder.check.CheckStore
import io.github.davamix.pillsreminder.check.HyperOsStep
import io.github.davamix.pillsreminder.check.InMemoryCheckStore
import io.github.davamix.pillsreminder.check.LineStatus
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import io.github.davamix.pillsreminder.reminders.MutableClock
import io.github.davamix.pillsreminder.ui.FakeCheckDevice
import io.github.davamix.pillsreminder.ui.allSet
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.deps
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

/**
 * Settings' reminder check (RM-9, P7 SPEC §4.3.7–§4.3.10, P7-D5, P7-D6). As in `HomeViewModelTest`, each test keeps the
 * state collected and reads it after `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val clock = MutableClock(nowAt(nov(23, 10, 30)))

    /** A HyperOS phone with battery optimisation on: one line missing, the steps to confirm. */
    private val hyperOsPhone = allSet.copy(batteryOptimised = true, hyperOs = true)

    private var reconciles = 0

    private fun TestScope.settings(
        device: FakeCheckDevice = FakeCheckDevice(),
        store: CheckStore = InMemoryCheckStore(CheckRecord(firstLaunchDone = true)),
        firstLaunch: Boolean = false,
        saved: SavedStateHandle = SavedStateHandle(),
    ): SettingsViewModel {
        val deps =
            deps(
                InMemoryTreatmentRepository(StoredData.Readable(PillsData(emptyList()))),
                clock,
                checkDevice = device,
                checkStore = store,
                reconcile = { reconciles++ },
            )
        val viewModel = SettingsViewModel(deps, firstLaunch, saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun TestScope.state(viewModel: SettingsViewModel): SettingsState? {
        advanceUntilIdle()
        return viewModel.state.value
    }

    /** Each line's item and status, in order. */
    private fun TestScope.lines(viewModel: SettingsViewModel) = checkNotNull(state(viewModel)).check.lines.map { it.item to it.status }

    @Test
    fun `the lines come from the device and the record, in the check's order`() =
        vmTest {
            val store = InMemoryCheckStore(CheckRecord(firstLaunchDone = true, confirmed = setOf(HyperOsStep.AUTOSTART)))
            val settings = settings(FakeCheckDevice(hyperOsPhone), store)

            assertEquals(
                listOf(
                    CheckItem.NOTIFICATIONS to LineStatus.OK,
                    CheckItem.EXACT_ALARMS to LineStatus.OK,
                    CheckItem.BATTERY to LineStatus.MISSING,
                    CheckItem.AUTOSTART to LineStatus.CONFIRMED,
                    CheckItem.FLOATING_NOTIFICATIONS to LineStatus.TO_CONFIRM,
                    CheckItem.LOCK_SCREEN_NOTIFICATIONS to LineStatus.TO_CONFIRM,
                ),
                lines(settings),
            )
            assertTrue(checkNotNull(state(settings)).check.hyperOs)
        }

    @Test
    fun `refresh reads the device again`() =
        vmTest {
            val device = FakeCheckDevice(allSet.copy(batteryOptimised = true))
            val settings = settings(device)
            assertEquals(LineStatus.MISSING, lines(settings)[2].second)
            assertEquals(1, device.reads)

            device.checks = allSet
            settings.refresh()

            assertEquals(LineStatus.OK, lines(settings)[2].second)
            assertEquals(2, device.reads)
        }

    @Test
    fun `a step marked done is confirmed in the record, and unticked it is to confirm again`() =
        vmTest {
            val store = InMemoryCheckStore(CheckRecord(firstLaunchDone = true, confirmed = setOf(HyperOsStep.AUTOSTART)))
            val settings = settings(FakeCheckDevice(hyperOsPhone), store)

            settings.setConfirmed(HyperOsStep.LOCK_SCREEN_NOTIFICATIONS, true)
            assertEquals(LineStatus.CONFIRMED, lines(settings).toMap()[CheckItem.LOCK_SCREEN_NOTIFICATIONS])
            assertEquals(setOf(HyperOsStep.AUTOSTART, HyperOsStep.LOCK_SCREEN_NOTIFICATIONS), store.record.value.confirmed)

            settings.setConfirmed(HyperOsStep.AUTOSTART, false)
            assertEquals(LineStatus.TO_CONFIRM, lines(settings).toMap()[CheckItem.AUTOSTART])
            assertEquals(CheckRecord(firstLaunchDone = true, confirmed = setOf(HyperOsStep.LOCK_SCREEN_NOTIFICATIONS)), store.record.value)
        }

    @Test
    fun `a confirmation that can't be written says Not saved, and the step stays to confirm`() =
        vmTest {
            val settings = settings(FakeCheckDevice(hyperOsPhone), FailingCheckStore())

            settings.setConfirmed(HyperOsStep.AUTOSTART, true)

            val state = checkNotNull(state(settings))
            assertEquals(ChangeProblem.NOT_SAVED, state.problem)
            assertEquals(
                LineStatus.TO_CONFIRM,
                state.check.lines
                    .single { it.item == CheckItem.AUTOSTART }
                    .status,
            )
            settings.problemShown()
            assertNull(checkNotNull(state(settings)).problem)
        }

    @Test
    fun `until the record is read there is nothing to show`() =
        vmTest {
            assertNull(state(settings(store = NeverReadCheckStore())))
        }

    @Test
    fun `the permission is asked only at the first launch, and only when notifications aren't allowed`() =
        vmTest {
            val refused = allSet.copy(notificationsAllowed = false)

            assertTrue(checkNotNull(state(settings(FakeCheckDevice(refused), firstLaunch = true))).askNotifications)
            assertFalse(checkNotNull(state(settings(FakeCheckDevice(refused), firstLaunch = false))).askNotifications)
            assertFalse(checkNotNull(state(settings(FakeCheckDevice(allSet), firstLaunch = true))).askNotifications)
        }

    @Test
    fun `the permission is asked once, also when the screen is rebuilt`() =
        vmTest {
            val device = FakeCheckDevice(allSet.copy(notificationsAllowed = false))
            val saved = SavedStateHandle()
            val settings = settings(device, firstLaunch = true, saved = saved)
            assertTrue(checkNotNull(state(settings)).askNotifications)

            settings.notificationsRequested()

            assertFalse(checkNotNull(state(settings)).askNotifications)
            assertFalse(checkNotNull(state(settings(device, firstLaunch = true, saved = saved))).askNotifications)
        }

    @Test
    fun `a grant from the dialog reconciles the reminders and reads the device again`() =
        vmTest {
            val device = FakeCheckDevice(allSet.copy(notificationsAllowed = false))
            val settings = settings(device, firstLaunch = true)
            assertEquals(LineStatus.MISSING, lines(settings)[0].second)

            device.checks = allSet
            settings.notificationsAsked(granted = true)

            assertEquals(1, reconciles)
            assertEquals(LineStatus.OK, lines(settings)[0].second)
            assertFalse(checkNotNull(state(settings)).askNotifications)
        }

    @Test
    fun `a refusal reconciles nothing, and isn't asked again`() =
        vmTest {
            val settings = settings(FakeCheckDevice(allSet.copy(notificationsAllowed = false)), firstLaunch = true)

            settings.notificationsAsked(granted = false)

            assertEquals(0, reconciles)
            val state = checkNotNull(state(settings))
            assertFalse(state.askNotifications)
            assertEquals(LineStatus.MISSING, state.check.lines[0].status)
        }

    /** A check's record that can't be written: each update throws, as a full disk would. */
    private class FailingCheckStore : CheckStore {
        override val record = MutableStateFlow(CheckRecord(firstLaunchDone = true))

        override suspend fun update(transform: (CheckRecord) -> CheckRecord): Unit = throw IOException("full")
    }

    /** A check's record that is never read. */
    private class NeverReadCheckStore : CheckStore {
        override val record: Flow<CheckRecord> = MutableSharedFlow()

        override suspend fun update(transform: (CheckRecord) -> CheckRecord) = Unit
    }
}
