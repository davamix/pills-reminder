package io.github.davamix.pillsreminder.debug

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.reminders.TakeReminder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the debug screen's buttons and fields do. The defaults do nothing, for drawing the screen in tests. */
data class DebugActions(
    val onInterval: (String) -> Unit = {},
    val onFirstIn: (String) -> Unit = {},
    val onTwoMedicines: (Boolean) -> Unit = {},
    val onNoEnd: (Boolean) -> Unit = {},
    val onInstructions: (Boolean) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onEnd: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onReconcile: () -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onClearLog: () -> Unit = {},
)

/** The debug tools' screen (D-15, P5 SPEC §4.3.9). It refreshes only while it is visible. */
@Composable
fun DebugScreen(viewModel: DebugViewModel = viewModel(factory = DebugViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    val actions =
        DebugActions(
            onInterval = viewModel::setInterval,
            onFirstIn = viewModel::setFirstIn,
            onTwoMedicines = viewModel::setTwoMedicines,
            onNoEnd = viewModel::setNoEnd,
            onInstructions = viewModel::setInstructions,
            onCreate = viewModel::create,
            onEnd = viewModel::endTests,
            onDelete = viewModel::deleteTests,
            onReconcile = viewModel::reconcileNow,
            onAllowNotifications = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onClearLog = viewModel::clearLog,
        )
    DebugContent(state, actions)
}

/** The debug screen for a state; `null` while the first one loads. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugContent(
    state: DebugState?,
    actions: DebugActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.debug_title)) }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state == null) return@Column
            if (state.unreadable) Text(stringResource(R.string.debug_unreadable), color = MaterialTheme.colorScheme.error)
            state.failure?.let { Text(stringResource(R.string.debug_failed, it), color = MaterialTheme.colorScheme.error) }
            TestTreatment(state, actions)
            HorizontalDivider()
            RemindersSection(state, actions)
            HorizontalDivider()
            DependsOn(state, actions)
            HorizontalDivider()
            LogSection(state, actions)
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun TestTreatment(
    state: DebugState,
    actions: DebugActions,
) {
    Heading(stringResource(R.string.debug_section_test))
    OutlinedTextField(
        value = state.form.intervalText,
        onValueChange = actions.onInterval,
        label = { Text(stringResource(R.string.debug_every)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.form.firstInText,
        onValueChange = actions.onFirstIn,
        label = { Text(stringResource(R.string.debug_first_in)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    SwitchRow(stringResource(R.string.debug_two_medicines), state.form.twoMedicines, actions.onTwoMedicines)
    SwitchRow(stringResource(R.string.debug_no_end), state.form.noEnd, actions.onNoEnd)
    SwitchRow(stringResource(R.string.debug_instructions), state.form.instructions, actions.onInstructions)
    Button(onClick = actions.onCreate, enabled = state.form.settings != null && !state.unreadable, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.debug_create))
    }
    Text(stringResource(R.string.debug_test_count, state.testTreatments))
    // One under the other: side by side, a large font broke their words (T6's drawings).
    OutlinedButton(onClick = actions.onEnd, enabled = state.testTreatments > 0, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.debug_end))
    }
    OutlinedButton(onClick = actions.onDelete, enabled = state.testTreatments > 0, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.debug_delete))
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RemindersSection(
    state: DebugState,
    actions: DebugActions,
) {
    val none = stringResource(R.string.debug_none)
    Heading(stringResource(R.string.debug_section_reminders))
    Text(stringResource(R.string.debug_next_wake_up, state.nextWakeUp?.let { formatInstant(it, state.zone) } ?: none))
    val alarm = state.device.nextAlarmClock?.let { formatInstant(it, state.zone) }
    Text(
        when {
            alarm == null -> stringResource(R.string.debug_next_alarm_ours, none)
            state.device.nextAlarmClockIsOurs -> stringResource(R.string.debug_next_alarm_ours, alarm)
            else -> stringResource(R.string.debug_next_alarm_other, alarm)
        },
    )
    Text(stringResource(R.string.debug_in_shade, state.device.inShade))
    for (line in state.records) {
        val planned = formatLocal(line.key.plannedAt)
        Text(
            when (val record = line.record) {
                is TakeReminder.Alerted -> {
                    stringResource(
                        R.string.debug_record_alerted,
                        line.medicine,
                        planned,
                        record.count,
                        formatInstant(record.lastAt, state.zone),
                    )
                }

                is TakeReminder.Snoozed -> {
                    stringResource(R.string.debug_record_snoozed, line.medicine, planned, formatInstant(record.until, state.zone))
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    OutlinedButton(onClick = actions.onReconcile, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.debug_reconcile)) }
}

@Composable
private fun DependsOn(
    state: DebugState,
    actions: DebugActions,
) {
    val device = state.device
    Heading(stringResource(R.string.debug_section_depends))
    Text(
        stringResource(if (device.notificationsAllowed) R.string.debug_notifications_allowed else R.string.debug_notifications_not_allowed),
    )
    Text(stringResource(if (device.exactAlarms) R.string.debug_exact_allowed else R.string.debug_exact_not_allowed))
    Text(stringResource(if (device.batteryOptimised) R.string.debug_battery_on else R.string.debug_battery_off))
    Text(stringResource(R.string.debug_channel, device.channelImportance?.toString() ?: stringResource(R.string.debug_none)))
    if (!device.notificationsAllowed) {
        Button(onClick = actions.onAllowNotifications, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.debug_allow_notifications))
        }
    }
}

@Composable
private fun LogSection(
    state: DebugState,
    actions: DebugActions,
) {
    Heading(stringResource(R.string.debug_section_log))
    if (state.device.log.isEmpty()) Text(stringResource(R.string.debug_log_empty))
    for (line in state.device.log) {
        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
    OutlinedButton(onClick = actions.onClearLog, enabled = state.device.log.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.debug_clear_log))
    }
}

private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)

private fun formatInstant(
    instant: Instant,
    zone: ZoneId,
): String = dateTime.format(instant.atZone(zone))

private fun formatLocal(time: LocalDateTime): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(time)
