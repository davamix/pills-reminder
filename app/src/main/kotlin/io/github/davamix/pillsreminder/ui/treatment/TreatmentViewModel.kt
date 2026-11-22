package io.github.davamix.pillsreminder.ui.treatment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.endTreatment
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.model.treatment
import io.github.davamix.pillsreminder.core.schedule.TreatmentStatus
import io.github.davamix.pillsreminder.core.schedule.firstTake
import io.github.davamix.pillsreminder.core.schedule.status
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import io.github.davamix.pillsreminder.ui.common.attempt
import io.github.davamix.pillsreminder.ui.treatments.STOP_AFTER_MILLIS
import io.github.davamix.pillsreminder.ui.treatments.lastTake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * The Treatment screen's ViewModel (P6 SPEC §4.3.4): one treatment's medicines, worked out again when the data changes
 * and at each whole minute while the screen is visible (P6-D3). End early and Delete go through `core.actions`.
 */
class TreatmentViewModel(
    private val deps: ScreenDependencies,
    private val id: TreatmentId,
) : ViewModel() {
    private val problem = MutableStateFlow<ChangeProblem?>(null)
    private val deletedFlag = MutableStateFlow(false)

    /** True once the treatment is deleted, or found gone when deleting it: the screen goes back (§4.3.1). */
    val deleted: StateFlow<Boolean> = deletedFlag.asStateFlow()

    val state: StateFlow<TreatmentState?> =
        combine(deps.repository.data, deps.minuteTicks, problem, deletedFlag) { stored, _, problem, deleted ->
            // Once deleted, nothing more to show while the screen leaves: not "no longer exists".
            if (deleted) null else build(stored, id, ZonedDateTime.now(deps.clock), problem)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** Ends the treatment now (TR-11). A treatment already ended stays as it is, with no message: the screen shows it. */
    fun end() {
        viewModelScope.launch {
            problem.value = attempt { deps.repository.endTreatment(id, deps.clock) }.takeUnless { it == ChangeProblem.NOT_CHANGED }
        }
    }

    /** Deletes the treatment and everything recorded for it (TR-9). */
    fun delete() {
        viewModelScope.launch {
            when (val result = attempt { deps.repository.deleteTreatment(id) }) {
                null, ChangeProblem.NOT_CHANGED -> deletedFlag.value = true
                else -> problem.value = result
            }
        }
    }

    fun problemShown() {
        problem.value = null
    }

    companion object {
        fun factory(
            deps: ScreenDependencies,
            id: TreatmentId,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { TreatmentViewModel(deps, id) } }

        internal fun build(
            stored: StoredData,
            id: TreatmentId,
            now: ZonedDateTime,
            problem: ChangeProblem? = null,
        ): TreatmentState {
            val data = (stored as? StoredData.Readable)?.data ?: return TreatmentState.Unreadable
            val treatment = data.treatment(id) ?: return TreatmentState.Missing
            val status = data.status(id, now) ?: return TreatmentState.Missing
            val cards =
                treatment.medicines.map { medicine ->
                    val takes = checkNotNull(data.takesOf(medicine.id)) { "No takes for medicine ${medicine.id.value}" }
                    MedicineCard(
                        id = medicine.id,
                        name = medicine.name,
                        dose = medicine.dose,
                        instructions = medicine.instructions,
                        interval = medicine.schedule.interval,
                        duration = medicine.schedule.duration,
                        answered = takes.answeredCount,
                        count = takes.count,
                        last = takes.last,
                        next = takes.nextTake(now),
                        lastAnswered = takes.lastAnswered(now),
                    )
                }
            return TreatmentState.Ready(
                id = id,
                name = treatment.name,
                notes = treatment.notes,
                status = status,
                startsAt = if (status == TreatmentStatus.UPCOMING) data.firstTake(id) else null,
                endedAt = treatment.endedAt,
                lastTake = if (status == TreatmentStatus.FINISHED && treatment.endedAt == null) data.lastTake(treatment) else null,
                medicines = cards,
                canEdit = treatment.endedAt == null,
                canEnd = status == TreatmentStatus.ACTIVE || status == TreatmentStatus.UPCOMING,
                today = now.toLocalDate(),
                problem = problem,
            )
        }
    }
}
