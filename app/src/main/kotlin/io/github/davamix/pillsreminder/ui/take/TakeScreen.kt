package io.github.davamix.pillsreminder.ui.take

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies

/**
 * One take: its planned time, state and time taken; Taken, Skip, Change time and Undo, as P2's rules allow (TK-2, TK-5).
 * [medicineId] and [plannedAt] are `TakeRoute`'s, as a tapped reminder opens it (RM-6, P5).
 */
@Composable
fun TakeScreen(
    deps: ScreenDependencies,
    medicineId: String,
    plannedAt: String,
    onBack: () -> Unit,
    viewModel: TakeViewModel = viewModel(factory = TakeViewModel.factory(deps, TakeViewModel.keyOf(medicineId, plannedAt))),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TakeContent(
        state = state,
        onBack = onBack,
        onTaken = viewModel::markTaken,
        onSkip = viewModel::skip,
        onUndo = viewModel::undo,
        onSetTakenTime = viewModel::setTakenTime,
        onProblemShown = viewModel::problemShown,
    )
}
