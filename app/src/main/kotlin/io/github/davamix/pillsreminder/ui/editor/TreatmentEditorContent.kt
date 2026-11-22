package io.github.davamix.pillsreminder.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.MedicineDraft
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.ValidationError
import io.github.davamix.pillsreminder.core.model.toSchedule
import io.github.davamix.pillsreminder.ui.common.BackButton
import io.github.davamix.pillsreminder.ui.common.ConfirmDialog
import io.github.davamix.pillsreminder.ui.common.DateTimeText
import io.github.davamix.pillsreminder.ui.common.ShowProblem
import io.github.davamix.pillsreminder.ui.common.medicineAndDose
import io.github.davamix.pillsreminder.ui.common.rememberDateTimeText
import io.github.davamix.pillsreminder.ui.common.scheduleText
import java.time.LocalDate

/**
 * The treatment editor: the name, the notes and the medicines (TR-2, TR-5, TR-9). Leaving with unsaved changes asks
 * first (TR-13). Stateless apart from its open dialogs: it draws [state], `null` until the stored treatment is read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentEditorContent(
    state: EditorState?,
    onLeave: () -> Unit,
    onName: (String) -> Unit,
    onNotes: (String) -> Unit,
    onEditMedicine: (MedicineId?) -> Unit,
    onRemoveMedicine: (MedicineId) -> Unit,
    onSave: () -> Unit,
    onProblemShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    ShowProblem(state?.problem, snackbar, onProblemShown)
    var discarding by rememberSaveable { mutableStateOf(false) }
    val hasChanges = state?.hasChanges == true
    val leave = { if (hasChanges) discarding = true else onLeave() }
    BackHandler(enabled = hasChanges) { discarding = true }
    val title = if (state?.isNew != false) R.string.editor_new_treatment else R.string.editor_edit_treatment
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = { BackButton(leave) },
                actions = {
                    if (state != null) {
                        TextButton(onClick = onSave, enabled = !state.saving) { Text(stringResource(R.string.editor_save)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state != null) {
            Form(state, Modifier.padding(padding), onName, onNotes, onEditMedicine, onRemoveMedicine)
        }
    }
    if (discarding) {
        ConfirmDialog(
            text = stringResource(R.string.editor_discard),
            confirm = stringResource(R.string.editor_discard_action),
            dismiss = stringResource(R.string.editor_keep_editing),
            onConfirm = {
                discarding = false
                onLeave()
            },
            onDismiss = { discarding = false },
        )
    }
}

@Composable
private fun Form(
    state: EditorState,
    modifier: Modifier,
    onName: (String) -> Unit,
    onNotes: (String) -> Unit,
    onEditMedicine: (MedicineId?) -> Unit,
    onRemoveMedicine: (MedicineId) -> Unit,
) {
    val text = rememberDateTimeText()
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val nameMissing = state.showErrors && ValidationError.TreatmentNameMissing in state.errors
    val noMedicine = state.showErrors && ValidationError.NoMedicine in state.errors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.unreadable) UnreadableBanner()
        OutlinedTextField(
            value = state.draft.name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.editor_treatment_name)) },
            isError = nameMissing,
            supportingText = if (nameMissing) ({ Text(stringResource(R.string.editor_error_treatment_name)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.draft.notes,
            onValueChange = onNotes,
            label = { Text(stringResource(R.string.editor_treatment_notes)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.editor_medicines), style = MaterialTheme.typography.titleMedium)
        if (noMedicine) {
            Text(
                stringResource(R.string.editor_error_no_medicine),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.draft.medicines.forEach { medicine ->
            MedicineRow(
                medicine = medicine,
                text = text,
                today = state.today,
                onOpen = { onEditMedicine(medicine.id) },
                onRemove = {
                    if (medicine.id in state.started) removing = medicine.id.value else onRemoveMedicine(medicine.id)
                },
            )
        }
        OutlinedButton(onClick = { onEditMedicine(null) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.editor_add_medicine), modifier = Modifier.padding(start = 8.dp))
        }
    }
    val medicine = removing?.let { id -> state.draft.medicines.firstOrNull { it.id.value == id } }
    if (medicine != null) {
        ConfirmDialog(
            text = stringResource(R.string.editor_remove_confirm, medicine.name),
            confirm = stringResource(R.string.editor_remove_action),
            onConfirm = {
                removing = null
                onRemoveMedicine(medicine.id)
            },
            onDismiss = { removing = null },
        )
    }
}

/** One medicine of the draft: its name and dose, schedule and first take. It opens the medicine editor. */
@Composable
private fun MedicineRow(
    medicine: MedicineDraft,
    text: DateTimeText,
    today: LocalDate,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val schedule = medicine.toSchedule()
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(medicineAndDose(medicine.name, medicine.dose), style = MaterialTheme.typography.titleMedium)
                Text(scheduleText(schedule.interval, schedule.duration), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.editor_first_take_line, text.dateTime(schedule.firstTake, today)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_remove_medicine, medicine.name))
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

/** The stored data can't be read: Save won't store anything (plan §2.4, P3 Q1). */
@Composable
internal fun UnreadableBanner() {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.problem_unreadable),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
