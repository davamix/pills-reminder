package io.github.davamix.pillsreminder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davamix.pillsreminder.check.CheckRecord
import io.github.davamix.pillsreminder.check.reminderCheck
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.TreatmentStatus
import io.github.davamix.pillsreminder.core.schedule.firstTake
import io.github.davamix.pillsreminder.core.schedule.status
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.core.time.dueAt
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import io.github.davamix.pillsreminder.ui.common.attempt
import io.github.davamix.pillsreminder.ui.treatments.STOP_AFTER_MILLIS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Home's ViewModel (P7 SPEC §4.3): the next take of each medicine and the upcoming treatments, worked out again when the
 * data changes and at each whole minute while the screen is visible (P6-D3); and the reminder check's warning, from the
 * device and the check's record (RM-9).
 */
class HomeViewModel(
    private val deps: ScreenDependencies,
) : ViewModel() {
    private val problem = MutableStateFlow<ChangeProblem?>(null)

    /**
     * The takes being marked Taken from here (§4.3.4): `null` while the change runs; once applied, the stored data it
     * changed. Taken stays disabled until the rows show data other than that, so a double tap marks once.
     */
    private val marks = MutableStateFlow<Map<TakeKey, StoredData?>>(emptyMap())

    /** What the device says, read at the start and each time Home is resumed (§4.3.9). */
    private val device = MutableStateFlow(deps.checkDevice.read())

    /** The first launch's check was opened from here, maybe before the record is written (§4.3.10). */
    private val checkOpenedHere = MutableStateFlow(false)

    private val rows: Flow<HomeData?> =
        combine(deps.repository.data, deps.minuteTicks, marks) { stored, _, marks ->
            val disabled = marks.filterValues { it == null || it == stored }.keys
            stored to build(stored, ZonedDateTime.now(deps.clock), disabled)
        }.onEach { (stored, _) ->
            // A change applied here has reached the rows once they show other data: Taken follows P2 again.
            marks.update { current -> current.filterValues { it == null || it == stored } }
        }.map<Pair<StoredData, HomeData>, HomeData?> { it.second }
            .onStart { emit(null) }

    /** The check's record, `null` until it is read. */
    private val record: Flow<CheckRecord?> =
        deps.checkStore.record
            .map<CheckRecord, CheckRecord?> { it }
            .onStart { emit(null) }

    private val check: Flow<CheckPart> =
        combine(device, record, checkOpenedHere) { device, record, openedHere ->
            // Until the record is read, no warning and no first launch: HyperOS's steps would all count as to confirm.
            if (record == null) {
                CheckPart(toCheck = 0, openCheck = false)
            } else {
                CheckPart(reminderCheck(device, record).toCheck, openCheck = !record.firstLaunchDone && !openedHere)
            }
        }

    val state: StateFlow<HomeState> =
        combine(rows, check, problem) { rows, check, problem -> HomeState(rows, check.toCheck, check.openCheck, problem) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), HomeState())

    /** Taken on a Due row, or Taken early on a Next row once confirmed (TK-2, TK-4). A take already being marked is ignored. */
    fun markTaken(key: TakeKey) {
        if (key in marks.value) return
        marks.update { it + (key to null) }
        viewModelScope.launch {
            val before = deps.repository.data.first()
            val result = attempt { deps.repository.markTaken(key, deps.clock) }
            if (result == null) {
                marks.update { it + (key to before) }
            } else {
                marks.update { it - key }
                problem.value = result
            }
        }
    }

    /** Reads the device again: Home is resumed, maybe after a change in Android's settings (§4.3.9). */
    fun refreshCheck() {
        device.value = deps.checkDevice.read()
    }

    /** The first launch's check is open (§4.3.10): never again, here at once, and in the record once written. */
    fun checkOpened() {
        checkOpenedHere.value = true
        viewModelScope.launch {
            try {
                deps.checkStore.update { it.copy(firstLaunchDone = true) }
            } catch (e: IOException) {
                // Not written: the check opens again at the next launch, which is harmless.
            }
        }
    }

    fun problemShown() {
        problem.value = null
    }

    private data class CheckPart(
        val toCheck: Int,
        val openCheck: Boolean,
    )

    companion object {
        fun factory(deps: ScreenDependencies): ViewModelProvider.Factory = viewModelFactory { initializer { HomeViewModel(deps) } }

        /**
         * The sections at [now] (§4.3.2): Due and Next by planned time, then treatment name, then the medicine's place;
         * Upcoming by start. Taken is disabled for the takes in [disabled].
         */
        internal fun build(
            stored: StoredData,
            now: ZonedDateTime,
            disabled: Set<TakeKey> = emptySet(),
        ): HomeData {
            val data = (stored as? StoredData.Readable)?.data ?: return HomeData.Unreadable
            val takes = mutableListOf<Ordered<HomeRow>>()
            val upcoming = mutableListOf<Ordered<UpcomingRow>>()
            data.treatments.forEach { treatment ->
                when (data.status(treatment.id, now)) {
                    TreatmentStatus.ACTIVE -> takes += rowsOf(data, treatment, now, disabled)
                    TreatmentStatus.UPCOMING ->
                        data.firstTake(treatment.id)?.let { start ->
                            val at = start.dueAt(now.zone)
                            upcoming += Ordered(UpcomingRow(treatment.id, treatment.name, start, timeLeft(now, at)), at, treatment.name, 0)
                        }
                    TreatmentStatus.FINISHED, null -> Unit
                }
            }
            val (due, next) = takes.sortedWith(ORDER).partition { it.due }
            return HomeData.Ready(
                due = due.map { it.row },
                next = next.map { it.row },
                upcoming = upcoming.sortedWith(ORDER).map { it.row },
                today = now.toLocalDate(),
            )
        }

        /** Each medicine's next take: Due or Pending (HM-1). A medicine with nothing left has none. */
        private fun rowsOf(
            data: PillsData,
            treatment: Treatment,
            now: ZonedDateTime,
            disabled: Set<TakeKey>,
        ): List<Ordered<HomeRow>> =
            treatment.medicines.mapIndexedNotNull { position, medicine ->
                val takes = data.takesOf(medicine.id) ?: return@mapIndexedNotNull null
                val take = takes.nextTake(now) ?: return@mapIndexedNotNull null
                val at = take.key.plannedAt.dueAt(now.zone)
                val row =
                    HomeRow(
                        key = take.key,
                        treatmentId = treatment.id,
                        treatment = treatment.name,
                        medicine = medicine.name,
                        dose = medicine.dose,
                        plannedAt = take.key.plannedAt,
                        timeLeft = timeLeft(now, at),
                        answered = takes.answeredCount,
                        count = takes.count,
                        // Offered exactly when P2's own rule would apply it now (P6-D5).
                        canMarkTaken = take.key !in disabled && data.markTaken(take.key, now) != null,
                    )
                Ordered(row, at, treatment.name, position, due = take.state == TakeState.DUE)
            }

        private fun timeLeft(
            now: ZonedDateTime,
            at: Instant,
        ) = TimeLeft.between(now.toInstant(), at)

        private val ORDER: Comparator<Ordered<*>> =
            compareBy<Ordered<*>> { it.at }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.treatment }
                .thenBy { it.position }
    }

    /** A row with what orders it: its planned instant, its treatment's name, its medicine's place. */
    private class Ordered<T>(
        val row: T,
        val at: Instant,
        val treatment: String,
        val position: Int,
        val due: Boolean = false,
    )
}
