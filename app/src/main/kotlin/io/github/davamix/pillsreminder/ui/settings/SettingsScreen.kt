package io.github.davamix.pillsreminder.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.ui.common.PlaceholderScreen

/** Settings, with the reminder check. A placeholder, replaced in P7. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.settings_title),
        replacedIn = "P7",
        onBack = onBack,
    )
}
