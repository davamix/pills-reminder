package io.github.davamix.pillsreminder.ui.take

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.ui.common.PlaceholderScreen

/** One take: its planned time, state and time taken. A placeholder, replaced in P6. */
@Composable
fun TakeScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.take_title),
        replacedIn = "P6",
        onBack = onBack,
    )
}
