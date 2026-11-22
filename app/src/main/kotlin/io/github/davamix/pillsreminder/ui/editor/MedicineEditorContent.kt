package io.github.davamix.pillsreminder.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.DurationUnit
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.ValidationError
import io.github.davamix.pillsreminder.ui.common.BackButton
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ConfirmDialog
import io.github.davamix.pillsreminder.ui.common.DateField
import io.github.davamix.pillsreminder.ui.common.DateTimeText
import io.github.davamix.pillsreminder.ui.common.ShowProblem
import io.github.davamix.pillsreminder.ui.common.TimeField
import io.github.davamix.pillsreminder.ui.common.isLargeFont
import io.github.davamix.pillsreminder.ui.common.rememberDateTimeText
import java.time.LocalDate
import java.time.LocalTime

/** The callbacks of the medicine editor's fields. */
data class MedicineFieldActions(
    val onName: (String) -> Unit = {},
    val onDose: (String) -> Unit = {},
    val onInstructions: (String) -> Unit = {},
    val onInterval: (String) -> Unit = {},
    val onIntervalUnit: (IntervalUnit) -> Unit = {},
    val onDuration: (String) -> Unit = {},
    val onDurationUnit: (DurationUnit) -> Unit = {},
    val onNoEnd: (Boolean) -> Unit = {},
    val onFirstTakeDate: (LocalDate) -> Unit = {},
    val onFirstTakeTime: (LocalTime) -> Unit = {},
)

/**
 * The medicine editor (TR-3, TR-4, TR-6, TR-7): its fields, the number of takes and the last one (SC-2, SC-5), and
 * notes for a first take in the past and for a medicine that has started (TR-10). Done puts the medicine into the
 * treatment's draft (P6 Q1); leaving with changes asks first (TR-13). Stateless apart from its open dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineEditorContent(
    editing: MedicineEditing?,
    today: LocalDate,
    problem: ChangeProblem?,
    actions: MedicineFieldActions,
    onDone: () -> Unit,
    onLeave: () -> Unit,
    onProblemShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    ShowProblem(problem, snackbar, onProblemShown)
    var discarding by rememberSaveable { mutableStateOf(false) }
    val hasChanges = editing?.hasChanges == true
    val leave = { if (hasChanges) discarding = true else onLeave() }
    BackHandler(enabled = hasChanges) { discarding = true }
    val title = if (editing?.isNew != false) R.string.editor_new_medicine else R.string.editor_edit_medicine
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = { BackButton(leave) },
                actions = {
                    if (editing != null) TextButton(onClick = onDone) { Text(stringResource(R.string.editor_done)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (editing != null) MedicineForm(editing, today, actions, Modifier.padding(padding))
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
private fun MedicineForm(
    editing: MedicineEditing,
    today: LocalDate,
    actions: MedicineFieldActions,
    modifier: Modifier,
) {
    val text = rememberDateTimeText()
    val form = editing.form
    val draft = form.draft

    fun shows(error: (ValidationError) -> Boolean) = editing.showErrors && editing.errors.any(error)
    val nameMissing = shows { it is ValidationError.MedicineNameMissing }
    val intervalInvalid = shows { it is ValidationError.IntervalInvalid }
    val minutesRefused = shows { it is ValidationError.MinutesNotAllowed }
    val durationInvalid = shows { it is ValidationError.DurationInvalid }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = actions.onName,
            label = { Text(stringResource(R.string.editor_medicine_name)) },
            isError = nameMissing,
            supportingText = if (nameMissing) ({ Text(stringResource(R.string.editor_error_medicine_name)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.dose,
            onValueChange = actions.onDose,
            label = { Text(stringResource(R.string.editor_dose)) },
            placeholder = { Text(stringResource(R.string.editor_dose_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.instructions,
            onValueChange = actions.onInstructions,
            label = { Text(stringResource(R.string.editor_instructions)) },
            placeholder = { Text(stringResource(R.string.editor_instructions_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AmountAndUnit(
            label = stringResource(R.string.editor_every),
            amountText = form.intervalText,
            onAmount = actions.onInterval,
            error =
                when {
                    intervalInvalid -> stringResource(R.string.editor_error_amount)
                    minutesRefused -> stringResource(R.string.editor_error_minutes)
                    else -> null
                },
            unit = draft.intervalUnit,
            units = IntervalUnit.entries.filter { it != IntervalUnit.MINUTES || editing.allowMinutes || it == draft.intervalUnit },
            unitName = { intervalUnitName(it, draft.intervalAmount) },
            onUnit = actions.onIntervalUnit,
        )
        AmountAndUnit(
            label = stringResource(R.string.editor_for),
            amountText = form.durationText,
            onAmount = actions.onDuration,
            error = if (durationInvalid) stringResource(R.string.editor_error_duration) else null,
            unit = draft.durationUnit,
            units = DurationUnit.entries,
            unitName = { durationUnitName(it, draft.durationAmount) },
            onUnit = actions.onDurationUnit,
            enabled = !draft.noEnd,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = draft.noEnd, role = Role.Switch, onValueChange = actions.onNoEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.editor_no_end), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = draft.noEnd, onCheckedChange = null)
        }
        FirstTake(editing, today, text, actions)
        Notes(editing)
        PreviewCard(editing.preview, today, text)
    }
}

/** The first take's date and time: side by side, or one under the other at a large font scale (NF-7). */
@Composable
private fun FirstTake(
    editing: MedicineEditing,
    today: LocalDate,
    text: DateTimeText,
    actions: MedicineFieldActions,
) {
    val firstTake = editing.form.draft.firstTake
    val dateLabel = stringResource(R.string.editor_first_take_date)
    val timeLabel = stringResource(R.string.editor_first_take_time)
    Text(stringResource(R.string.editor_first_take), style = MaterialTheme.typography.titleSmall)
    if (isLargeFont()) {
        DateField(dateLabel, firstTake.toLocalDate(), today, text, actions.onFirstTakeDate, Modifier.fillMaxWidth())
        TimeField(timeLabel, firstTake.toLocalTime(), text, actions.onFirstTakeTime, Modifier.fillMaxWidth())
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(dateLabel, firstTake.toLocalDate(), today, text, actions.onFirstTakeDate, Modifier.weight(1f))
            TimeField(timeLabel, firstTake.toLocalTime(), text, actions.onFirstTakeTime, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Notes(editing: MedicineEditing) {
    if (editing.firstTakeInPast) {
        Text(stringResource(R.string.editor_note_past), style = MaterialTheme.typography.bodyMedium)
    }
    if (editing.started) {
        Text(stringResource(R.string.editor_note_started), style = MaterialTheme.typography.bodyMedium)
    }
}

/** "21 takes. The last one is on Mon 30 Nov at 00:00." (SC-2), "No end: …" (SC-5), or what is missing. */
@Composable
private fun PreviewCard(
    preview: Preview?,
    today: LocalDate,
    text: DateTimeText,
) {
    val count = preview?.count
    val last = preview?.last
    val line =
        when {
            preview == null -> stringResource(R.string.editor_preview_missing)
            count == null || last == null -> stringResource(R.string.editor_preview_no_end)
            else ->
                pluralStringResource(
                    R.plurals.editor_preview,
                    count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    text.number(count),
                    text.date(last.toLocalDate(), today),
                    text.time(last),
                )
        }
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
    }
}

/** An amount as digits, and its unit from a dropdown: "Every [8] [hours ▾]". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> AmountAndUnit(
    label: String,
    amountText: String,
    onAmount: (String) -> Unit,
    error: String?,
    unit: T,
    units: List<T>,
    unitName: @Composable (T) -> String,
    onUnit: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmount,
                label = { Text(label) },
                isError = error != null,
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(AMOUNT_WIDTH),
            )
            ExposedDropdownMenuBox(
                expanded = expanded && enabled,
                onExpandedChange = { if (enabled) expanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = unitName(unit),
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.editor_unit)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                    units.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(unitName(option)) },
                            onClick = {
                                expanded = false
                                onUnit(option)
                            },
                        )
                    }
                }
            }
        }
        // Under the whole row, not under the narrow amount, where it would break words at a large font scale (NF-7).
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

private val AMOUNT_WIDTH = 128.dp

/** "hour" or "hours", as the amount reads. A missing amount reads as plural. */
@Composable
private fun intervalUnitName(
    unit: IntervalUnit,
    amount: Int?,
): String {
    val plural =
        when (unit) {
            IntervalUnit.MINUTES -> R.plurals.unit_minutes
            IntervalUnit.HOURS -> R.plurals.unit_hours
            IntervalUnit.DAYS -> R.plurals.unit_days
            IntervalUnit.WEEKS -> R.plurals.unit_weeks
        }
    return pluralStringResource(plural, amount ?: 2)
}

/** "day" or "days", as the amount reads. */
@Composable
private fun durationUnitName(
    unit: DurationUnit,
    amount: Int?,
): String =
    pluralStringResource(
        when (unit) {
            DurationUnit.DAYS -> R.plurals.unit_days
            DurationUnit.WEEKS -> R.plurals.unit_weeks
        },
        amount ?: 2,
    )
