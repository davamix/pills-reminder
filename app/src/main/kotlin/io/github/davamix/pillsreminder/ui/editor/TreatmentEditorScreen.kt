package io.github.davamix.pillsreminder.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.ui.common.PlaceholderAction
import io.github.davamix.pillsreminder.ui.common.PlaceholderScreen

/** The treatment editor: name, notes and medicines. A placeholder, replaced in P6. */
@Composable
fun TreatmentEditorScreen(
    onBack: () -> Unit,
    onAddMedicine: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.treatment_editor_title),
        replacedIn = "P6",
        onBack = onBack,
        actions = listOf(PlaceholderAction(stringResource(R.string.treatment_editor_add_medicine), onAddMedicine)),
    )
}
