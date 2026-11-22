package io.github.davamix.pillsreminder.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davamix.pillsreminder.core.model.MedicineId
import java.time.LocalDate

/**
 * The medicine editor (TR-3, TR-4, TR-6, TR-7, SC-2, SC-5). Its [viewModel] is the editors' shared one (P6-D1).
 * [medicineId] is the draft's medicine to edit, or `null` for a new one; [entry] is this screen's back-stack entry, which
 * keeps the working copy across a restart of the process (P6-D6). [onClose] returns to the treatment editor, after Done
 * or after leaving without keeping the changes.
 */
@Composable
fun MedicineEditorScreen(
    viewModel: EditorViewModel,
    medicineId: MedicineId?,
    entry: String,
    onClose: () -> Unit,
) {
    LaunchedEffect(entry) { viewModel.openMedicine(medicineId, entry) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MedicineEditorContent(
        editing = state?.medicine,
        // Never drawn: without a working copy the editor shows nothing. (LocalDate.EPOCH needs API 34; the phone is 33.)
        today = state?.today ?: LocalDate.MIN,
        problem = null,
        actions =
            MedicineFieldActions(
                onName = viewModel::setMedicineName,
                onDose = viewModel::setDose,
                onInstructions = viewModel::setInstructions,
                onInterval = viewModel::setInterval,
                onIntervalUnit = viewModel::setIntervalUnit,
                onDuration = viewModel::setDuration,
                onDurationUnit = viewModel::setDurationUnit,
                onNoEnd = viewModel::setNoEnd,
                onFirstTakeDate = viewModel::setFirstTakeDate,
                onFirstTakeTime = viewModel::setFirstTakeTime,
            ),
        onDone = { if (viewModel.medicineDone()) onClose() },
        onLeave = {
            viewModel.closeMedicine()
            onClose()
        },
        onProblemShown = {},
    )
}
