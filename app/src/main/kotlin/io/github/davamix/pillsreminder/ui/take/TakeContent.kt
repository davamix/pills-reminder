package io.github.davamix.pillsreminder.ui.take

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.schedule.Take
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.ui.common.BackButton
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.DateField
import io.github.davamix.pillsreminder.ui.common.DateTimeText
import io.github.davamix.pillsreminder.ui.common.ShowProblem
import io.github.davamix.pillsreminder.ui.common.TimeField
import io.github.davamix.pillsreminder.ui.common.UnreadableMessage
import io.github.davamix.pillsreminder.ui.common.medicineAndDose
import io.github.davamix.pillsreminder.ui.common.rememberDateTimeText
import io.github.davamix.pillsreminder.ui.common.takeStateText
import io.github.davamix.pillsreminder.ui.common.takenText
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** The actions P2's rules allow on the take now (P6-D5). */
data class TakeActions(
    val taken: Boolean = false,
    val skip: Boolean = false,
    val undo: Boolean = false,
    val changeTime: Boolean = false,
) {
    val any: Boolean get() = taken || skip || undo || changeTime
}

/** What the Take screen shows. `null` while the stored data hasn't been read. */
sealed interface TakeScreenState {
    data object Unreadable : TakeScreenState

    /** There is no such take: its treatment was deleted, its medicine removed, or its schedule edited. */
    data object Missing : TakeScreenState

    data class Ready(
        val medicine: String,
        val dose: String,
        val instructions: String,
        val treatment: String,
        val take: Take,
        /** Whether it is its medicine's last answered take (TK-5). */
        val isLastAnswered: Boolean,
        val actions: TakeActions,
        val now: ZonedDateTime,
        val problem: ChangeProblem? = null,
    ) : TakeScreenState
}

/**
 * One take: its planned time, state and time taken; Taken, Skip, Change time and Undo, as P2's rules allow (TK-2,
 * TK-5). Stateless apart from the open dialog: it draws [state].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeContent(
    state: TakeScreenState?,
    onBack: () -> Unit,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onSetTakenTime: (LocalDateTime) -> Unit,
    onProblemShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    ShowProblem((state as? TakeScreenState.Ready)?.problem, snackbar, onProblemShown)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.take_title)) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (state) {
            null -> Unit
            TakeScreenState.Unreadable -> UnreadableMessage(Modifier.padding(padding))
            TakeScreenState.Missing ->
                Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.take_missing), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.take_missing_text), style = MaterialTheme.typography.bodyMedium)
                }
            is TakeScreenState.Ready ->
                TakeDetails(state, Modifier.padding(padding), onTaken, onSkip, onUndo, onSetTakenTime)
        }
    }
}

@Composable
private fun TakeDetails(
    state: TakeScreenState.Ready,
    modifier: Modifier,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onSetTakenTime: (LocalDateTime) -> Unit,
) {
    val text = rememberDateTimeText()
    val take = state.take
    val today = state.now.toLocalDate()
    var changing by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(medicineAndDose(state.medicine, state.dose), style = MaterialTheme.typography.headlineSmall)
        Text(state.treatment, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.instructions.isNotBlank()) Text(state.instructions, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.take_planned, text.dateTime(take.key.plannedAt, today)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(takeStateText(take.state), style = MaterialTheme.typography.titleLarge, color = stateColour(take.state))
        takenText(take, text, today)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (take.state == TakeState.DUE) {
            Text(
                stringResource(R.string.take_missed_at, text.dateTime(take.missedAt, today)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val answered = take.state == TakeState.TAKEN || take.state == TakeState.SKIPPED
        if (answered && !state.isLastAnswered) {
            Text(stringResource(R.string.take_only_last), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Actions(state.actions, onTaken, onSkip, onUndo, onChangeTime = { changing = true })
    }
    if (changing) {
        ChangeTimeDialog(
            initial = take.takenAt ?: state.now.toLocalDateTime(),
            now = state.now,
            text = text,
            onSave = {
                changing = false
                onSetTakenTime(it)
            },
            onDismiss = { changing = false },
        )
    }
}

/** The take's buttons, full width, one under the other (P5's lesson at font scale 2). */
@Composable
private fun Actions(
    actions: TakeActions,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onChangeTime: () -> Unit,
) {
    if (actions.taken) {
        Button(onClick = onTaken, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.take_taken)) }
    }
    if (actions.skip) {
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.take_skip)) }
    }
    if (actions.changeTime) {
        OutlinedButton(onClick = onChangeTime, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.take_change_time))
        }
    }
    if (actions.undo) {
        OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.take_undo)) }
    }
}

@Composable
private fun stateColour(state: TakeState): Color =
    when (state) {
        TakeState.DUE -> MaterialTheme.colorScheme.primary
        TakeState.MISSED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

/** The date and time a take was taken, as two fields (TK-2). A time after now is refused here, as P2 refuses it. */
@Composable
private fun ChangeTimeDialog(
    initial: LocalDateTime,
    now: ZonedDateTime,
    text: DateTimeText,
    onSave: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime()) }
    val chosen = LocalDateTime.of(date, time)
    val future = chosen > now.toLocalDateTime()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.take_change_time_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(stringResource(R.string.take_change_time_date), date, now.toLocalDate(), text, { date = it })
                TimeField(stringResource(R.string.take_change_time_time), time, text, { time = it })
                if (future) {
                    Text(
                        stringResource(R.string.take_change_time_future),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(chosen) },
                enabled = !future,
            ) { Text(stringResource(R.string.take_change_time_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
