package io.github.davamix.pillsreminder.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davamix.pillsreminder.check.CheckItem
import io.github.davamix.pillsreminder.navigation.dropUnlessResumedWith
import io.github.davamix.pillsreminder.ui.common.LeaveWhen
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies

/**
 * Settings, with the reminder check (RM-9). Each line's button opens its settings screen, or Android's dialog for
 * battery optimisation, from this activity (P7 SPEC §4.3.8). Opened by Home at the first launch ([firstLaunch]), it asks
 * for the notification permission with Android's dialog, once (§4.3.10).
 */
@Composable
fun SettingsScreen(
    deps: ScreenDependencies,
    firstLaunch: Boolean,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(deps, firstLaunch)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The check follows a change made in Android's settings, or in its dialog, as soon as the screen is back (§4.3.9).
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> viewModel.notificationsAsked(granted) }
    // Driven by the state, once the screen is resumed, as navigation is (CLAUDE.md): an activity Android rebuilds is
    // composed before that.
    LeaveWhen(state?.askNotifications == true) {
        viewModel.notificationsRequested()
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    SettingsContent(
        state = state,
        onBack = onBack,
        // A tap counts only while the screen is resumed, as a click that navigates does (CLAUDE.md).
        onOpen = dropUnlessResumedWith { item: CheckItem -> deps.checkDevice.open(context, item) },
        onConfirm = viewModel::setConfirmed,
        onProblemShown = viewModel::problemShown,
    )
}
