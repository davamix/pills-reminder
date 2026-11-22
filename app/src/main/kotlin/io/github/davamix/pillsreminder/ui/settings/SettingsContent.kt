package io.github.davamix.pillsreminder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.check.CheckItem
import io.github.davamix.pillsreminder.check.CheckLine
import io.github.davamix.pillsreminder.check.HyperOsStep
import io.github.davamix.pillsreminder.check.LineStatus
import io.github.davamix.pillsreminder.check.NotificationsProblem
import io.github.davamix.pillsreminder.check.ReminderCheck
import io.github.davamix.pillsreminder.ui.common.BackButton
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ShowProblem

/** The test tag of the check's list, for `performScrollToNode` (CLAUDE.md). */
const val SETTINGS_LIST_TAG = "settings-list"

/** What Settings shows. `null` until the check's record is read. */
data class SettingsState(
    val check: ReminderCheck,
    /** The first launch's request for the notification permission (P7 SPEC §4.3.10). The screen acts on it. */
    val askNotifications: Boolean = false,
    /** A confirmation that couldn't be written. */
    val problem: ChangeProblem? = null,
)

/**
 * Settings: the reminder check (RM-9). Android's items, each with its button, then HyperOS's steps to confirm on a
 * HyperOS phone. Stateless: it draws [state].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsState?,
    onBack: () -> Unit,
    onOpen: (CheckItem) -> Unit,
    onConfirm: (HyperOsStep, Boolean) -> Unit,
    onProblemShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    ShowProblem(state?.problem, snackbar, onProblemShown)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state != null) CheckList(state.check, padding, onOpen, onConfirm)
    }
}

@Composable
private fun CheckList(
    check: ReminderCheck,
    padding: PaddingValues,
    onOpen: (CheckItem) -> Unit,
    onConfirm: (HyperOsStep, Boolean) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val (steps, android) = check.lines.partition { it.item.step != null }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(SETTINGS_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") { Intro(check) }
        items(android, key = { it.item.name }) { line -> LineCard(line, check.hyperOs, onOpen, onConfirm) }
        if (steps.isNotEmpty()) {
            item(key = "hyperos") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Heading(R.string.settings_hyperos_heading)
                    Text(stringResource(R.string.settings_hyperos_intro), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(steps, key = { it.item.name }) { line -> LineCard(line, check.hyperOs, onOpen, onConfirm) }
        }
    }
}

@Composable
private fun Heading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Intro(check: ReminderCheck) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Heading(R.string.settings_check_heading)
        Text(stringResource(R.string.settings_check_intro), style = MaterialTheme.typography.bodyMedium)
        val summary =
            if (check.toCheck == 0) {
                stringResource(R.string.settings_check_all_set)
            } else {
                pluralStringResource(R.plurals.settings_check_to_check, check.toCheck, check.toCheck)
            }
        Text(summary, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One line: its status icon, its name and what it says, then its buttons, one under the other (P7 SPEC §4.3.11). */
@Composable
private fun LineCard(
    line: CheckLine,
    hyperOs: Boolean,
    onOpen: (CheckItem) -> Unit,
    onConfirm: (HyperOsStep, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                StatusIcon(line.status)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(title(line.item)), style = MaterialTheme.typography.titleMedium)
                    val missing = line.status == LineStatus.MISSING
                    val color = if (missing) MaterialTheme.colorScheme.error else Color.Unspecified
                    Text(lineText(line), style = MaterialTheme.typography.bodyMedium, color = color)
                    if (line.item == CheckItem.BATTERY && missing && hyperOs) {
                        Text(stringResource(R.string.settings_battery_hyperos), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            LineButtons(line, onOpen, onConfirm)
        }
    }
}

@Composable
private fun StatusIcon(status: LineStatus) {
    val (icon, tint, description) =
        when (status) {
            LineStatus.OK -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, R.string.settings_status_ok)
            LineStatus.CONFIRMED -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, R.string.settings_status_confirmed)
            LineStatus.MISSING -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, R.string.settings_status_missing)
            LineStatus.TO_CONFIRM -> Triple(Icons.Filled.Info, MaterialTheme.colorScheme.tertiary, R.string.settings_status_to_confirm)
        }
    Icon(icon, contentDescription = stringResource(description), tint = tint)
}

@Composable
private fun LineButtons(
    line: CheckLine,
    onOpen: (CheckItem) -> Unit,
    onConfirm: (HyperOsStep, Boolean) -> Unit,
) {
    val missing = line.status == LineStatus.MISSING
    when {
        line.item == CheckItem.BATTERY ->
            if (missing) ActionButton(R.string.settings_turn_off, primary = true) { onOpen(line.item) }
        line.item == CheckItem.EXACT_ALARMS ->
            if (missing) ActionButton(R.string.settings_open, primary = true) { onOpen(line.item) }
        line.item == CheckItem.NOTIFICATIONS -> ActionButton(R.string.settings_open, primary = missing) { onOpen(line.item) }
        else -> {
            val step = checkNotNull(line.item.step)
            ActionButton(R.string.settings_open, primary = false) { onOpen(line.item) }
            HorizontalDivider()
            DoneBox(line.status == LineStatus.CONFIRMED) { onConfirm(step, it) }
        }
    }
}

/** A full-width button: tonal for what is missing, outlined otherwise. */
@Composable
private fun ActionButton(
    label: Int,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
    if (primary) {
        FilledTonalButton(onClick = onClick, modifier = modifier) { Text(stringResource(label)) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(stringResource(label)) }
    }
}

/** "Done", a checkbox row at least 48 dp tall (NF-7): ticked once the user has confirmed the step. */
@Composable
private fun DoneBox(
    done: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = done, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.settings_done), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun title(item: CheckItem): Int =
    when (item) {
        CheckItem.NOTIFICATIONS -> R.string.settings_notifications
        CheckItem.EXACT_ALARMS -> R.string.settings_exact_alarms
        CheckItem.BATTERY -> R.string.settings_battery
        CheckItem.AUTOSTART -> R.string.settings_autostart
        CheckItem.FLOATING_NOTIFICATIONS -> R.string.settings_floating
        CheckItem.LOCK_SCREEN_NOTIFICATIONS -> R.string.settings_lock_screen
    }

/** What a line says: its state for Android's items, the step to take for HyperOS's. */
@Composable
private fun lineText(line: CheckLine): String {
    val ok = line.status == LineStatus.OK
    return when (line.item) {
        CheckItem.NOTIFICATIONS ->
            stringResource(
                when {
                    ok -> R.string.settings_notifications_ok
                    line.problem == NotificationsProblem.CHANNEL_OFF -> R.string.settings_notifications_channel_off
                    line.problem == NotificationsProblem.NO_POP_UP -> R.string.settings_notifications_no_pop_up
                    else -> R.string.settings_notifications_not_allowed
                },
            )
        CheckItem.EXACT_ALARMS -> stringResource(if (ok) R.string.settings_exact_alarms_ok else R.string.settings_exact_alarms_missing)
        CheckItem.BATTERY -> stringResource(if (ok) R.string.settings_battery_ok else R.string.settings_battery_missing)
        CheckItem.AUTOSTART -> stringResource(R.string.settings_autostart_text, stringResource(R.string.app_name))
        CheckItem.FLOATING_NOTIFICATIONS -> stringResource(R.string.settings_floating_text)
        CheckItem.LOCK_SCREEN_NOTIFICATIONS -> stringResource(R.string.settings_lock_screen_text)
    }
}
