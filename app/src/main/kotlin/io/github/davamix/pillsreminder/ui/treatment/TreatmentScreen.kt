package io.github.davamix.pillsreminder.ui.treatment

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.ui.common.PlaceholderAction
import io.github.davamix.pillsreminder.ui.common.PlaceholderScreen

/** One treatment: its medicines, their progress, and each one's last answered take. A placeholder, replaced in P6. */
@Composable
fun TreatmentScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTake: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.treatment_title),
        replacedIn = "P6",
        onBack = onBack,
        actions =
            listOf(
                PlaceholderAction(stringResource(R.string.treatment_edit), onEdit),
                PlaceholderAction(stringResource(R.string.treatment_open_take), onOpenTake),
            ),
    )
}
