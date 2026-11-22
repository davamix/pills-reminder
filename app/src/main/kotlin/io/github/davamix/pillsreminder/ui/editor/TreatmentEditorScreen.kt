package io.github.davamix.pillsreminder.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.ui.common.LeaveWhen

/**
 * The treatment editor (TR-2, TR-5, TR-9, TR-13). Its [viewModel] is the editors' shared one, from their navigation
 * graph (P6-D1). [onLeave] leaves the editors; [onEditMedicine] opens the medicine editor, on a new medicine when
 * `null`; [onSaved] is called once the treatment is stored, after the screen is resumed.
 */
@Composable
fun TreatmentEditorScreen(
    viewModel: EditorViewModel,
    onLeave: () -> Unit,
    onEditMedicine: (MedicineId?) -> Unit,
    onSaved: (TreatmentId) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedAs by viewModel.savedAs.collectAsStateWithLifecycle()
    LeaveWhen(savedAs != null) { savedAs?.let(onSaved) }
    TreatmentEditorContent(
        state = state,
        onLeave = onLeave,
        onName = viewModel::setName,
        onNotes = viewModel::setNotes,
        onEditMedicine = onEditMedicine,
        onRemoveMedicine = viewModel::removeMedicine,
        onSave = viewModel::save,
        onProblemShown = viewModel::problemShown,
    )
}
