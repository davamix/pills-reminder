package io.github.davamix.pillsreminder.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.ui.common.LeaveWhen
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies

/**
 * Home: the next takes, the upcoming treatments and the reminder check's warning (HM-1–HM-5, RM-9). [onSettings] opens
 * Settings, from the settings button and the warning's Check; [onOpenCheck] opens it at the first launch (P7 SPEC
 * §4.3.10), once, as navigation driven by the state.
 */
@Composable
fun HomeScreen(
    deps: ScreenDependencies,
    onOpenTreatment: (TreatmentId) -> Unit,
    onAllTreatments: () -> Unit,
    onNewTreatment: () -> Unit,
    onSettings: () -> Unit,
    onOpenCheck: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(deps)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The check follows a change made in Android's settings as soon as Home is back (§4.3.9).
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshCheck()
        onPauseOrDispose {}
    }
    LeaveWhen(state.openCheck) {
        viewModel.checkOpened()
        onOpenCheck()
    }
    HomeContent(
        state = state,
        onOpenTreatment = onOpenTreatment,
        onTaken = viewModel::markTaken,
        onAllTreatments = onAllTreatments,
        onNewTreatment = onNewTreatment,
        onSettings = onSettings,
        onCheck = onSettings,
        onProblemShown = viewModel::problemShown,
    )
}
