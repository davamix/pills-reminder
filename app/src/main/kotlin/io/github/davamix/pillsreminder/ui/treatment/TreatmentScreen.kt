package io.github.davamix.pillsreminder.ui.treatment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.ui.common.LeaveWhen
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies

/**
 * One treatment: its medicines with their progress, next take and last answered take; Edit, End early and Delete (TR-9,
 * TR-11, TK-5). After a delete it calls [onDeleted], once resumed.
 */
@Composable
fun TreatmentScreen(
    deps: ScreenDependencies,
    treatmentId: TreatmentId,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTake: (TakeKey) -> Unit,
    onDeleted: () -> Unit,
    viewModel: TreatmentViewModel = viewModel(factory = TreatmentViewModel.factory(deps, treatmentId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    LeaveWhen(deleted, onDeleted)
    TreatmentContent(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onOpenTake = onOpenTake,
        onEnd = viewModel::end,
        onDelete = viewModel::delete,
        onProblemShown = viewModel::problemShown,
    )
}
