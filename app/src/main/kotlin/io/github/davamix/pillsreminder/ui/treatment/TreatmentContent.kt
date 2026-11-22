package io.github.davamix.pillsreminder.ui.treatment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.schedule.Take
import io.github.davamix.pillsreminder.core.schedule.TreatmentStatus
import io.github.davamix.pillsreminder.ui.common.BackButton
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ConfirmDialog
import io.github.davamix.pillsreminder.ui.common.DateTimeText
import io.github.davamix.pillsreminder.ui.common.ShowProblem
import io.github.davamix.pillsreminder.ui.common.UnreadableMessage
import io.github.davamix.pillsreminder.ui.common.medicineAndDose
import io.github.davamix.pillsreminder.ui.common.rememberDateTimeText
import io.github.davamix.pillsreminder.ui.common.scheduleText
import io.github.davamix.pillsreminder.ui.common.statusText
import io.github.davamix.pillsreminder.ui.common.takeOutcome
import io.github.davamix.pillsreminder.ui.common.takeStateText
import java.time.LocalDate
import java.time.LocalDateTime

/** The test tag of the screen's list, for `performScrollToNode` (CLAUDE.md). */
const val TREATMENT_LIST_TAG = "treatment-list"

/** One medicine of the treatment, as its card shows it (P6 SPEC §4.3.4). */
data class MedicineCard(
    val id: MedicineId,
    val name: String,
    val dose: String,
    val instructions: String,
    val interval: Interval,
    val duration: MedicineDuration,
    /** Takes answered Taken or Skipped (HM-3). */
    val answered: Int,
    /** How many takes there are; `null` for no end (SC-5). */
    val count: Long?,
    /** The last take; `null` for no end. */
    val last: LocalDateTime?,
    /** The next take (TK-4's rule), or `null` once none is left. */
    val next: Take?,
    /** The last answered take (TK-5), or `null` while none is answered. */
    val lastAnswered: Take?,
)

/** What the Treatment screen shows. `null` while the stored data hasn't been read. */
sealed interface TreatmentState {
    data object Unreadable : TreatmentState

    /** There is no treatment with that ID: deleted, or never there. */
    data object Missing : TreatmentState

    data class Ready(
        val id: TreatmentId,
        val name: String,
        val notes: String,
        val status: TreatmentStatus,
        /** An Upcoming treatment's first take. */
        val startsAt: LocalDateTime?,
        /** When it was ended early (TR-11), or `null`. */
        val endedAt: LocalDateTime?,
        /** A Finished treatment's last take, when it wasn't ended early. */
        val lastTake: LocalDateTime?,
        val medicines: List<MedicineCard>,
        /** Edit is offered unless it was ended early (P6 Q4). */
        val canEdit: Boolean,
        /** End early is offered while Active or Upcoming (TR-11). */
        val canEnd: Boolean,
        val today: LocalDate,
        val problem: ChangeProblem? = null,
    ) : TreatmentState
}

/** The confirmation the screen is asking for. */
private enum class Confirm { END, DELETE }

/**
 * One treatment: its medicines with their schedule, progress, next take and last answered take; Edit, End early and
 * Delete (TR-9, TR-11, TK-5). Stateless apart from which confirmation is open: it draws [state].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentContent(
    state: TreatmentState?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTake: (TakeKey) -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
    onProblemShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val ready = state as? TreatmentState.Ready
    ShowProblem(ready?.problem, snackbar, onProblemShown)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.treatment_title)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (ready?.canEdit == true) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.treatment_edit))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (state) {
            null -> Unit
            TreatmentState.Unreadable -> UnreadableMessage(Modifier.padding(padding))
            TreatmentState.Missing ->
                Text(
                    text = stringResource(R.string.treatment_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            is TreatmentState.Ready -> TreatmentDetails(state, padding, onOpenTake, onEnd, onDelete)
        }
    }
}

@Composable
private fun TreatmentDetails(
    state: TreatmentState.Ready,
    padding: PaddingValues,
    onOpenTake: (TakeKey) -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    val text = rememberDateTimeText()
    val direction = LocalLayoutDirection.current
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TREATMENT_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header(state, text) }
        items(state.medicines, key = { it.id.value }) { medicine ->
            MedicineCardView(medicine, text, state.today, onOpenTake)
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (state.canEnd) {
                    OutlinedButton(onClick = { confirm = Confirm.END }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.treatment_end))
                    }
                }
                Button(
                    onClick = { confirm = Confirm.DELETE },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(stringResource(R.string.treatment_delete))
                }
            }
        }
    }
    when (confirm) {
        Confirm.END ->
            ConfirmDialog(
                text = stringResource(R.string.treatment_end_confirm, state.name),
                confirm = stringResource(R.string.treatment_end_action),
                onConfirm = {
                    confirm = null
                    onEnd()
                },
                onDismiss = { confirm = null },
            )
        Confirm.DELETE ->
            ConfirmDialog(
                text = stringResource(R.string.treatment_delete_confirm, state.name),
                confirm = stringResource(R.string.treatment_delete_action),
                onConfirm = {
                    confirm = null
                    onDelete()
                },
                onDismiss = { confirm = null },
            )
        null -> Unit
    }
}

@Composable
private fun Header(
    state: TreatmentState.Ready,
    text: DateTimeText,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(state.name, style = MaterialTheme.typography.headlineSmall)
        val status = statusText(state.status)
        val detail =
            when {
                state.startsAt != null -> stringResource(R.string.treatment_starts, text.dateTime(state.startsAt, state.today))
                state.endedAt != null -> stringResource(R.string.treatment_ended, text.dateTime(state.endedAt, state.today))
                state.lastTake != null -> stringResource(R.string.treatment_last_take, text.dateTime(state.lastTake, state.today))
                else -> null
            }
        Text(
            text = if (detail == null) status else stringResource(R.string.treatment_status_detail, status, detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.notes.isNotBlank()) Text(state.notes, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MedicineCardView(
    medicine: MedicineCard,
    text: DateTimeText,
    today: LocalDate,
    onOpenTake: (TakeKey) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(medicineAndDose(medicine.name, medicine.dose), style = MaterialTheme.typography.titleMedium)
            if (medicine.instructions.isNotBlank()) Text(medicine.instructions, style = MaterialTheme.typography.bodyMedium)
            Text(scheduleText(medicine.interval, medicine.duration), style = MaterialTheme.typography.bodyMedium)
            if (medicine.count == 0L) {
                // Ended before its first take: no takes at all, which isn't "no end" (P6 T8, M1).
                Text(stringResource(R.string.treatment_no_takes), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(progress(medicine, text), style = MaterialTheme.typography.bodyMedium)
                val last = medicine.last?.let { text.dateTime(it, today) } ?: stringResource(R.string.no_end)
                Text(stringResource(R.string.treatment_medicine_last_take, last), style = MaterialTheme.typography.bodyMedium)
            }
            if (medicine.next != null || medicine.lastAnswered != null) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            medicine.next?.let { take ->
                TakeLink(
                    label = stringResource(R.string.treatment_next, text.dateTime(take.key.plannedAt, today), takeStateText(take.state)),
                    onClick = { onOpenTake(take.key) },
                )
            }
            medicine.lastAnswered?.let { take ->
                TakeLink(
                    label =
                        stringResource(
                            R.string.treatment_last_answered,
                            text.dateTime(take.key.plannedAt, today),
                            takeOutcome(take, text, today),
                        ),
                    onClick = { onOpenTake(take.key) },
                )
            }
        }
    }
}

/** "1 of 21 taken or skipped", or "1 taken or skipped · No end" (HM-3, SC-5). */
@Composable
private fun progress(
    medicine: MedicineCard,
    text: DateTimeText,
): String {
    val answered = text.number(medicine.answered.toLong())
    return medicine.count?.let { stringResource(R.string.treatment_progress, answered, text.number(it)) }
        ?: stringResource(R.string.treatment_progress_no_end, answered)
}

/** A take that opens the Take screen: a whole row, at least 48 dp tall (NF-7). */
@Composable
private fun TakeLink(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
