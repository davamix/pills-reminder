package io.github.davamix.pillsreminder.ui

import android.content.Context
import io.github.davamix.pillsreminder.check.ChannelState
import io.github.davamix.pillsreminder.check.CheckItem
import io.github.davamix.pillsreminder.check.CheckRecord
import io.github.davamix.pillsreminder.check.CheckStore
import io.github.davamix.pillsreminder.check.DeviceChecks
import io.github.davamix.pillsreminder.check.InMemoryCheckStore
import io.github.davamix.pillsreminder.check.ReminderCheckDevice
import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.Schedule
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Clock
import java.time.LocalDateTime

// Helpers for P6's ViewModel tests. The repo is public: test data is made up, after the spec's §4.4 example.

/**
 * Runs a ViewModel test with the main dispatcher on the test's scheduler, so `viewModelScope` runs in virtual time.
 * Main is set before `runTest`, which then uses its scheduler, and reset only after `runTest` returns: `stateIn`'s stop
 * timer is still pending in `viewModelScope` when the test body ends, and would dispatch to an unset Main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun vmTest(block: suspend TestScope.() -> Unit) {
    Dispatchers.setMain(StandardTestDispatcher())
    try {
        runTest { block() }
    } finally {
        Dispatchers.resetMain()
    }
}

/** The minute ticks, sent by the test itself (P6-D3): one at once, then one at each [tick]. */
class TestTicks {
    val flow = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    fun tick() {
        flow.tryEmit(Unit)
    }
}

fun deps(
    repository: TreatmentRepository,
    clock: Clock,
    ticks: TestTicks = TestTicks(),
    allowMinutes: Boolean = true,
    checkDevice: ReminderCheckDevice = FakeCheckDevice(),
    checkStore: CheckStore = InMemoryCheckStore(CheckRecord(firstLaunchDone = true)),
    reconcile: () -> Unit = {},
) = ScreenDependencies(repository, clock, allowMinutes, ticks.flow, checkDevice, checkStore, reconcile)

/** Everything reminders need is set, on a phone that isn't HyperOS: the check has nothing to warn about. */
val allSet =
    DeviceChecks(notificationsAllowed = true, channel = ChannelState.ON, exactAlarms = true, batteryOptimised = false, hyperOs = false)

/** The reminder check's device, as the test sets it (P7-D2). It counts its readings and records what it opened. */
class FakeCheckDevice(
    var checks: DeviceChecks = allSet,
) : ReminderCheckDevice {
    var reads = 0
        private set
    val opened = mutableListOf<CheckItem>()

    override fun read(): DeviceChecks {
        reads++
        return checks
    }

    override fun open(
        activity: Context,
        item: CheckItem,
    ) {
        opened += item
    }
}

/** The first state that isn't `null` and matches [predicate], collected in the foreground (CLAUDE.md, coroutine tests). */
suspend fun <T : Any> StateFlow<T?>.next(predicate: (T) -> Boolean = { true }): T = filterNotNull().first(predicate)

/** A medicine of one schedule: every [every] [unit] for [days] days, or with no end. */
fun medicine(
    id: String,
    name: String,
    firstTake: LocalDateTime,
    days: Int?,
    every: Int = 8,
    unit: IntervalUnit = IntervalUnit.HOURS,
) = Medicine(
    id = MedicineId(id),
    name = name,
    dose = "1 tablet",
    instructions = "",
    schedule = Schedule(firstTake, Interval(every, unit), days?.let { MedicineDuration.Days(it) } ?: MedicineDuration.NoEnd),
)

fun treatment(
    id: String,
    name: String,
    vararg medicines: Medicine,
    endedAt: LocalDateTime? = null,
) = Treatment(TreatmentId(id), name, "", medicines.toList(), endedAt)
