package io.github.davamix.pillsreminder.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.ui.common.PlaceholderScreen

/** The medicine editor: dose, interval, duration and first take. A placeholder, replaced in P6. */
@Composable
fun MedicineEditorScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.medicine_editor_title),
        replacedIn = "P6",
        onBack = onBack,
    )
}
