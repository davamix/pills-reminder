package io.github.davamix.pillsreminder.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first

/**
 * Calls [onLeave] once [leave] turns true, after waiting for the screen to be resumed: navigation driven by a ViewModel
 * state, such as leaving after a save or a delete (CLAUDE.md). A screen Android rebuilds is composed before it is
 * resumed, and `dropUnlessResumed` would drop the call then.
 */
@Composable
fun LeaveWhen(
    leave: Boolean,
    onLeave: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnLeave by rememberUpdatedState(onLeave)
    LaunchedEffect(leave) {
        if (!leave) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        currentOnLeave()
    }
}
