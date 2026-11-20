package io.github.davamix.pillsreminder.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.ui.common.PlaceholderAction
import io.github.davamix.pillsreminder.ui.common.PlaceholderScreen

/** The home screen with the next takes. A placeholder, replaced in P7. */
@Composable
fun HomeScreen(
    onAllTreatments: () -> Unit,
    onNewTreatment: () -> Unit,
    onSettings: () -> Unit,
    onSampleTake: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.home_title),
        replacedIn = "P7",
        onBack = null,
        actions =
            listOf(
                PlaceholderAction(stringResource(R.string.home_all_treatments), onAllTreatments),
                PlaceholderAction(stringResource(R.string.home_new_treatment), onNewTreatment),
                PlaceholderAction(stringResource(R.string.home_open_settings), onSettings),
                PlaceholderAction(stringResource(R.string.home_sample_take), onSampleTake),
            ),
    )
}
