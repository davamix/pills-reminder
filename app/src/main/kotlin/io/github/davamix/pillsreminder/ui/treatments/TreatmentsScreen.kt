package io.github.davamix.pillsreminder.ui.treatments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies

/** Every treatment, grouped as Active, Upcoming and Finished (TR-1, TR-12). */
@Composable
fun TreatmentsScreen(
    deps: ScreenDependencies,
    onBack: () -> Unit,
    onOpen: (TreatmentId) -> Unit,
    onNew: () -> Unit,
    viewModel: TreatmentsViewModel = viewModel(factory = TreatmentsViewModel.factory(deps)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TreatmentsContent(state = state, onBack = onBack, onOpen = onOpen, onNew = onNew)
}
