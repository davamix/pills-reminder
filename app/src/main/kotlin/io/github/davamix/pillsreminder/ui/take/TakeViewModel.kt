package io.github.davamix.pillsreminder.ui.take

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davamix.pillsreminder.core.actions.clearAnswer
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.actions.setTakenTime
import io.github.davamix.pillsreminder.core.actions.skip
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.medicine
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import io.github.davamix.pillsreminder.ui.common.attempt
import io.github.davamix.pillsreminder.ui.treatments.STOP_AFTER_MILLIS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/**
 * The Take screen's ViewModel (P6 SPEC §4.3.5): one take, worked out again when the data changes and at each whole
 * minute while the screen is visible (P6-D3). It offers exactly the answers P2's rules allow now (P6-D5), and applies
 * them through `core.actions`; the reminder follows by itself (P5-D2). [key] is `null` when the route's time doesn't
 * parse: the take is then missing.
 */
class TakeViewModel(
    private val deps: ScreenDependencies,
    private val key: TakeKey?,
) : ViewModel() {
    private val problem = MutableStateFlow<ChangeProblem?>(null)

    val state: StateFlow<TakeScreenState?> =
        combine(deps.repository.data, deps.minuteTicks, problem) { stored, _, problem ->
            build(stored, key, ZonedDateTime.now(deps.clock), problem)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** Taken now (TK-2): a Due take, the next take early (TK-4), or the last answered take when Skipped (TK-5). */
    fun markTaken() = act { deps.repository.markTaken(it, deps.clock) }

    /** Skipped: a Due take, or the last answered take when Taken (TK-5). */
    fun skip() = act { deps.repository.skip(it, deps.clock) }

    /** Back to unanswered (TK-5): the take is Pending, Due or Missed again, by its times. */
    fun undo() = act { deps.repository.clearAnswer(it, deps.clock) }

    /** Corrects the time the last answered take was taken (TK-2). */
    fun setTakenTime(at: LocalDateTime) = act { deps.repository.setTakenTime(it, at, deps.clock) }

    fun problemShown() {
        problem.value = null
    }

    private fun act(change: suspend (TakeKey) -> Boolean) {
        val key = key ?: return
        viewModelScope.launch { problem.value = attempt { change(key) } }
    }

    companion object {
        fun factory(
            deps: ScreenDependencies,
            key: TakeKey?,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { TakeViewModel(deps, key) } }

        /** The take a `TakeRoute` names, or `null` if its time doesn't parse (P5: `LocalDateTime.toString()` form). */
        fun keyOf(
            medicineId: String,
            plannedAt: String,
        ): TakeKey? =
            try {
                TakeKey(MedicineId(medicineId), LocalDateTime.parse(plannedAt))
            } catch (e: DateTimeParseException) {
                null
            }

        internal fun build(
            stored: StoredData,
            key: TakeKey?,
            now: ZonedDateTime,
            problem: ChangeProblem? = null,
        ): TakeScreenState {
            val data = (stored as? StoredData.Readable)?.data ?: return TakeScreenState.Unreadable
            if (key == null) return TakeScreenState.Missing
            val (treatment, medicine) = data.medicine(key.medicineId) ?: return TakeScreenState.Missing
            val takes = data.takesOf(key.medicineId) ?: return TakeScreenState.Missing
            val take = takes.take(key.plannedAt, now) ?: return TakeScreenState.Missing
            // Each action is offered when P2's own rule would apply it now (P6-D5).
            val actions =
                TakeActions(
                    taken = data.markTaken(key, now) != null,
                    skip = data.skip(key, now) != null,
                    undo = data.clearAnswer(key, now) != null,
                    changeTime = data.setTakenTime(key, now.toLocalDateTime(), now) != null,
                )
            return TakeScreenState.Ready(
                medicine = medicine.name,
                dose = medicine.dose,
                instructions = medicine.instructions,
                treatment = treatment.name,
                take = take,
                isLastAnswered = takes.lastAnswered(now)?.key == key,
                actions = actions,
                now = now,
                problem = problem,
            )
        }
    }
}
