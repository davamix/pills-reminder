package io.github.davamix.pillsreminder.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davamix.pillsreminder.PillsReminderApp
import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.endTreatment
import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.model.medicine
import io.github.davamix.pillsreminder.core.reminders.ReminderPlanner
import io.github.davamix.pillsreminder.core.reminders.TakeReminder
import io.github.davamix.pillsreminder.reminders.ReconcileTrigger
import io.github.davamix.pillsreminder.reminders.Reminders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** The test treatment's settings as typed: the numbers may be empty or out of range until they are fixed. */
data class DebugForm(
    val intervalText: String = TestSettings().intervalMinutes.toString(),
    val firstInText: String = TestSettings().firstInMinutes.toString(),
    val twoMedicines: Boolean = false,
    val noEnd: Boolean = false,
) {
    /** The settings, or `null` while a number is missing or out of range. */
    val settings: TestSettings?
        get() {
            val interval = intervalText.toIntOrNull()?.takeIf { it >= 1 } ?: return null
            val firstIn = firstInText.toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return TestSettings(interval, firstIn, twoMedicines, noEnd)
        }
}

/** One reminder record, as the screen shows it: Snoozed or Alerted (P4). */
data class RecordLine(
    val medicine: String,
    val key: TakeKey,
    val record: TakeReminder,
)

/** Everything the debug screen shows (P5 SPEC §4.3.9). */
data class DebugState(
    val form: DebugForm = DebugForm(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val unreadable: Boolean = false,
    val testTreatments: Int = 0,
    /** The plan's next wake-up now: where the alarm should be. */
    val nextWakeUp: Instant? = null,
    val records: List<RecordLine> = emptyList(),
    val device: DeviceSnapshot = DeviceSnapshot(),
    /** The class of the last action's failure, if it failed: never its message (plan §3). */
    val failure: String? = null,
)

/** The debug tools' ViewModel (D-15). Debug builds only. It changes the data only through P2's helpers (P5-D2). */
class DebugViewModel(
    private val repository: TreatmentRepository,
    private val clock: Clock,
    private val reminders: Reminders,
    private val device: DebugDevice,
) : ViewModel() {
    private val form = MutableStateFlow(DebugForm())
    private val failure = MutableStateFlow<String?>(null)
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The device, read again every 2 s and after each action, only while the screen collects [state]. */
    private val snapshots =
        merge(
            flow {
                while (true) {
                    emit(Unit)
                    delay(REFRESH_MILLIS)
                }
            },
            refreshes,
        ).map { device.read() }.flowOn(Dispatchers.IO)

    val state: StateFlow<DebugState?> =
        combine(repository.data, form, snapshots, failure) { stored, form, snapshot, failure ->
            build(stored, form, snapshot, failure)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    fun setInterval(text: String) = form.update { it.copy(intervalText = text.digits()) }

    fun setFirstIn(text: String) = form.update { it.copy(firstInText = text.digits()) }

    fun setTwoMedicines(two: Boolean) = form.update { it.copy(twoMedicines = two) }

    fun setNoEnd(noEnd: Boolean) = form.update { it.copy(noEnd = noEnd) }

    fun create() =
        act {
            val settings = form.value.settings ?: return@act
            repository.saveTreatment(TestSchedules.draft(settings, ZonedDateTime.now(clock)), clock)
        }

    /** Ends every test treatment now (TR-11): their Due reminders go. */
    fun endTests() = act { tests().forEach { repository.endTreatment(it, clock) } }

    /** Deletes every test treatment (TR-9). */
    fun deleteTests() = act { tests().forEach { repository.deleteTreatment(it) } }

    fun reconcileNow() = act { reminders.reconcile(ReconcileTrigger.DEBUG) }

    fun clearLog() = act { device.clearLog() }

    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    private suspend fun tests() = ((repository.data.first() as? StoredData.Readable)?.data ?: PillsData()).testIds()

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            failure.value =
                try {
                    block()
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e::class.simpleName
                }
            refresh()
        }
    }

    private fun build(
        stored: StoredData,
        form: DebugForm,
        snapshot: DeviceSnapshot,
        failure: String?,
    ): DebugState {
        val now = ZonedDateTime.now(clock)
        val data = (stored as? StoredData.Readable)?.data
        val records =
            data?.reminders?.takes.orEmpty().map { (key, record) ->
                RecordLine(data?.medicine(key.medicineId)?.second?.name ?: key.medicineId.value, key, record)
            }
        return DebugState(
            form = form,
            zone = now.zone,
            unreadable = data == null,
            testTreatments = data?.testIds()?.size ?: 0,
            nextWakeUp = data?.let { ReminderPlanner.plan(it, it.reminders, now).nextWakeUp },
            records = records.sortedBy { it.key.plannedAt },
            device = snapshot,
            failure = failure,
        )
    }

    companion object {
        private const val REFRESH_MILLIS = 2_000L
        private const val STOP_AFTER_MILLIS = 1_000L
        private const val MAX_DIGITS = 5

        private fun String.digits() = filter(Char::isDigit).take(MAX_DIGITS)

        private fun PillsData.testIds() = treatments.filter(TestSchedules::isTest).map { it.id }

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as PillsReminderApp
                    val container = app.container
                    DebugViewModel(container.repository, container.clock, container.reminders, AndroidDebugDevice(app))
                }
            }
    }
}
