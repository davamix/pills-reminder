package io.github.davamix.pillsreminder.ui.treatments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.schedule.TreatmentStatus
import io.github.davamix.pillsreminder.core.schedule.firstTake
import io.github.davamix.pillsreminder.core.schedule.status
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** How long a screen's state keeps running after its last collector goes, such as across a rotation. */
internal const val STOP_AFTER_MILLIS = 5_000L

/**
 * The Treatments screen's ViewModel (P6 SPEC §4.3.3): every treatment in its group, worked out again when the data
 * changes and at each whole minute while the screen is visible (P6-D3).
 */
class TreatmentsViewModel(
    deps: ScreenDependencies,
) : ViewModel() {
    val state: StateFlow<TreatmentsState?> =
        combine(deps.repository.data, deps.minuteTicks) { stored, _ -> build(stored, ZonedDateTime.now(deps.clock)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    companion object {
        fun factory(deps: ScreenDependencies): ViewModelProvider.Factory = viewModelFactory { initializer { TreatmentsViewModel(deps) } }

        /** The groups at [now]: Active by name, Upcoming soonest first, Finished latest first. */
        internal fun build(
            stored: StoredData,
            now: ZonedDateTime,
        ): TreatmentsState {
            val data = (stored as? StoredData.Readable)?.data ?: return TreatmentsState.Unreadable
            val rows = data.treatments.mapNotNull { treatment -> data.status(treatment.id, now)?.let { it to row(data, treatment, it) } }

            fun group(status: TreatmentStatus) = rows.filter { it.first == status }.map { it.second }
            return TreatmentsState.Ready(
                active = group(TreatmentStatus.ACTIVE).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
                upcoming = group(TreatmentStatus.UPCOMING).sortedBy { it.startsAt },
                finished = group(TreatmentStatus.FINISHED).sortedByDescending { it.endedAt ?: it.lastTake ?: LocalDateTime.MIN },
                today = now.toLocalDate(),
            )
        }

        private fun row(
            data: PillsData,
            treatment: Treatment,
            status: TreatmentStatus,
        ) = TreatmentRow(
            id = treatment.id,
            name = treatment.name,
            medicines = treatment.medicines.map { it.name },
            startsAt = if (status == TreatmentStatus.UPCOMING) data.firstTake(treatment.id) else null,
            endedAt = if (status == TreatmentStatus.FINISHED) treatment.endedAt else null,
            lastTake = if (status == TreatmentStatus.FINISHED && treatment.endedAt == null) data.lastTake(treatment) else null,
        )
    }
}

/** The latest last take of the treatment's medicines, or `null` if one has no end or none has a take. */
internal fun PillsData.lastTake(treatment: Treatment): LocalDateTime? = treatment.medicines.mapNotNull { takesOf(it.id)?.last }.maxOrNull()
