package io.github.davamix.pillsreminder.ui.treatments

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.ui.common.PlaceholderAction
import io.github.davamix.pillsreminder.ui.common.PlaceholderScreen

/** Every treatment, grouped as Active, Upcoming and Finished. A placeholder, replaced in P6. */
@Composable
fun TreatmentsScreen(
    onBack: () -> Unit,
    onOpenSample: () -> Unit,
    onNewTreatment: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.treatments_title),
        replacedIn = "P6",
        onBack = onBack,
        actions =
            listOf(
                PlaceholderAction(stringResource(R.string.treatments_open_sample), onOpenSample),
                PlaceholderAction(stringResource(R.string.treatments_new), onNewTreatment),
            ),
    )
}
