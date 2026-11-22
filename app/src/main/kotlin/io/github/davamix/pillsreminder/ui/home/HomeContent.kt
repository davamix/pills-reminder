package io.github.davamix.pillsreminder.ui.home

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.ui.common.ChangeProblem
import io.github.davamix.pillsreminder.ui.common.ConfirmDialog
import io.github.davamix.pillsreminder.ui.common.DateTimeText
import io.github.davamix.pillsreminder.ui.common.ShowProblem
import io.github.davamix.pillsreminder.ui.common.UnreadableMessage
import io.github.davamix.pillsreminder.ui.common.medicineAndDose
import io.github.davamix.pillsreminder.ui.common.rememberDateTimeText
import java.time.LocalDate
import java.time.LocalDateTime

/** The test tag of Home's list, for `performScrollToNode` (CLAUDE.md). */
const val HOME_LIST_TAG = "home-list"

/** Room below the list's last row for the floating button. */
private val FAB_SPACE = 88.dp

/**
 * From this font scale on, a row's button goes under its text, so no word breaks (P7 SPEC §4.3.11). At the phone's
 * "big" font, 1.4, a button beside the text left it so narrow that a dose broke between its number and its unit (T7, M5).
 */
private const val STACKED_FONT_SCALE = 1.3f

/** One medicine's next take on Home: a Due row or a Next row (HM-1–HM-3, P7 SPEC §4.3.3). */
data class HomeRow(
    val key: TakeKey,
    val treatmentId: TreatmentId,
    val treatment: String,
    val medicine: String,
    val dose: String,
    val plannedAt: LocalDateTime,
    val timeLeft: TimeLeft,
    /** Takes answered Taken or Skipped (HM-3). */
    val answered: Int,
    /** How many takes there are; `null` for no end (SC-5). */
    val count: Long?,
    /** A Due row's Taken is enabled; a Next row shows Taken early (TK-4). Both from P2's pure `markTaken`. */
    val canMarkTaken: Boolean,
)

/** An Upcoming treatment, with its first take (HM-4). */
data class UpcomingRow(
    val id: TreatmentId,
    val name: String,
    val startsAt: LocalDateTime,
    val timeLeft: TimeLeft,
)

/** Home's part that comes from the stored data. */
sealed interface HomeData {
    data object Unreadable : HomeData

    /** The sections, each in its order (P7 SPEC §4.3.2). All three empty: the empty state. */
    data class Ready(
        val due: List<HomeRow>,
        val next: List<HomeRow>,
        val upcoming: List<UpcomingRow>,
        val today: LocalDate,
    ) : HomeData {
        val isEmpty: Boolean get() = due.isEmpty() && next.isEmpty() && upcoming.isEmpty()
    }
}

/** What Home shows. */
data class HomeState(
    /** `null` until the stored data is read. */
    val data: HomeData? = null,
    /** The reminder check's lines to check; 0 shows no warning (RM-9, P7 SPEC §4.3.6). */
    val toCheck: Int = 0,
    /** The first launch: Home opens the check (P7 SPEC §4.3.10). The screen acts on it; the content ignores it. */
    val openCheck: Boolean = false,
    val problem: ChangeProblem? = null,
)

/**
 * Home: the next take of each medicine, due takes first, and the upcoming treatments (HM-1–HM-5); the reminder check's
 * warning (RM-9). Stateless apart from the Taken early confirmation: it draws [state].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeState,
    onOpenTreatment: (TreatmentId) -> Unit,
    onTaken: (TakeKey) -> Unit,
    onAllTreatments: () -> Unit,
    onNewTreatment: () -> Unit,
    onSettings: () -> Unit,
    onCheck: () -> Unit,
    onProblemShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    ShowProblem(state.problem, snackbar, onProblemShown)
    val data = state.data
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                TextButton(onClick = onAllTreatments, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_all_treatments))
                }
            }
        },
        floatingActionButton = {
            if (data is HomeData.Ready) {
                // The content overload: the one with `text` and `icon` hides its label from TalkBack and tests (P6).
                ExtendedFloatingActionButton(onClick = onNewTreatment) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.home_new_treatment))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        HomeList(state, padding, onOpenTreatment, onTaken, onCheck)
    }
}

@Composable
private fun HomeList(
    state: HomeState,
    padding: PaddingValues,
    onOpenTreatment: (TreatmentId) -> Unit,
    onTaken: (TakeKey) -> Unit,
    onCheck: () -> Unit,
) {
    val text = rememberDateTimeText()
    val direction = LocalLayoutDirection.current
    var confirmEarly by rememberSaveable(stateSaver = TakeKeySaver) { mutableStateOf<TakeKey?>(null) }
    val data = state.data
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(HOME_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                bottom = padding.calculateBottomPadding() + FAB_SPACE,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.toCheck > 0) item(key = "warning") { CheckWarning(state.toCheck, onCheck) }
        when (data) {
            null -> Unit
            HomeData.Unreadable -> item(key = "unreadable") { UnreadableMessage(Modifier.padding(top = 8.dp)) }
            is HomeData.Ready ->
                if (data.isEmpty) {
                    item(key = "empty") { EmptyHome() }
                } else {
                    sections(data, text, onOpenTreatment, onTaken, onTakenEarly = { confirmEarly = it })
                }
        }
    }
    val early = (data as? HomeData.Ready)?.next?.firstOrNull { it.key == confirmEarly }
    if (early != null) {
        ConfirmDialog(
            title = stringResource(R.string.home_taken_early_title),
            text =
                stringResource(
                    R.string.home_taken_early_confirm,
                    medicineAndDose(early.medicine, early.dose),
                    text.dateTime(early.plannedAt, (data as HomeData.Ready).today),
                ),
            confirm = stringResource(R.string.home_taken_early),
            onConfirm = {
                confirmEarly = null
                onTaken(early.key)
            },
            onDismiss = { confirmEarly = null },
        )
    }
}

private fun LazyListScope.sections(
    data: HomeData.Ready,
    text: DateTimeText,
    onOpenTreatment: (TreatmentId) -> Unit,
    onTaken: (TakeKey) -> Unit,
    onTakenEarly: (TakeKey) -> Unit,
) {
    if (data.due.isNotEmpty()) {
        item(key = "heading-due") { Heading(R.string.home_due) }
        items(data.due, key = { "due-${it.key}" }) { row ->
            TakeRow(row, due = true, text, data.today, onOpen = { onOpenTreatment(row.treatmentId) }, onTaken = { onTaken(row.key) })
        }
    }
    if (data.next.isNotEmpty()) {
        item(key = "heading-next") { Heading(R.string.home_next) }
        items(data.next, key = { "next-${it.key}" }) { row ->
            TakeRow(row, due = false, text, data.today, onOpen = { onOpenTreatment(row.treatmentId) }, onTaken = { onTakenEarly(row.key) })
        }
    }
    if (data.upcoming.isNotEmpty()) {
        item(key = "heading-upcoming") { Heading(R.string.home_upcoming) }
        items(data.upcoming, key = { "upcoming-${it.id.value}" }) { row ->
            UpcomingCard(row, text, data.today, onOpen = { onOpenTreatment(row.id) })
        }
    }
}

@Composable
private fun Heading(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** A Due row, highlighted, with Taken; or a Next row, with Taken early when P2 allows it. */
@Composable
private fun TakeRow(
    row: HomeRow,
    due: Boolean,
    text: DateTimeText,
    today: LocalDate,
    onOpen: () -> Unit,
    onTaken: () -> Unit,
) {
    val colors =
        if (due) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        }
    val stacked = LocalDensity.current.fontScale >= STACKED_FONT_SCALE
    val showButton = due || row.canMarkTaken
    Card(onClick = onOpen, colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowTexts(row, text, today, Modifier.weight(1f))
                if (showButton && !stacked) {
                    Spacer(Modifier.width(8.dp))
                    TakenButton(due, row.canMarkTaken, onTaken, Modifier)
                }
            }
            if (showButton && stacked) TakenButton(due, row.canMarkTaken, onTaken, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RowTexts(
    row: HomeRow,
    text: DateTimeText,
    today: LocalDate,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(medicineAndDose(row.medicine, row.dose), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.home_row_when, text.dateTime(row.plannedAt, today), timeLeftText(row.timeLeft)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(progress(row, text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TakenButton(
    due: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (due) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(stringResource(R.string.home_taken)) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(stringResource(R.string.home_taken_early)) }
    }
}

/** "Example · 1 of 21", or "Example · 5 answered · No end" (HM-3, SC-5). */
@Composable
private fun progress(
    row: HomeRow,
    text: DateTimeText,
): String {
    val answered = text.number(row.answered.toLong())
    return row.count?.let { stringResource(R.string.home_row_progress, row.treatment, answered, text.number(it)) }
        ?: stringResource(R.string.home_row_progress_no_end, row.treatment, answered)
}

@Composable
private fun UpcomingCard(
    row: UpcomingRow,
    text: DateTimeText,
    today: LocalDate,
    onOpen: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_upcoming_starts, text.dateTime(row.startsAt, today), timeLeftText(row.timeLeft)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The reminder check's warning (RM-9, P7 SPEC §4.3.6). */
@Composable
private fun CheckWarning(
    toCheck: Int,
    onCheck: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    pluralStringResource(R.plurals.home_check_warning, toCheck, toCheck),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            FilledTonalButton(onClick = onCheck, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.home_check_open))
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.home_empty_text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Keeps the Taken early confirmation's take through a rebuilt screen. */
private val TakeKeySaver =
    listSaver<TakeKey?, String>(
        save = { key -> if (key == null) emptyList() else listOf(key.medicineId.value, key.plannedAt.toString()) },
        restore = { saved -> if (saved.size == 2) TakeKey(MedicineId(saved[0]), LocalDateTime.parse(saved[1])) else null },
    )
