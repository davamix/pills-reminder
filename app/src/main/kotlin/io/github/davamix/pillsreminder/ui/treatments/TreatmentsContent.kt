package io.github.davamix.pillsreminder.ui.treatments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.ui.common.BackButton
import io.github.davamix.pillsreminder.ui.common.DateTimeText
import io.github.davamix.pillsreminder.ui.common.UnreadableMessage
import io.github.davamix.pillsreminder.ui.common.rememberDateTimeText
import java.time.LocalDate
import java.time.LocalDateTime

/** The test tag of the list, for `performScrollToNode` (CLAUDE.md). */
const val TREATMENTS_LIST_TAG = "treatments-list"

/** Room below the list's last row for the floating button. */
private val FAB_SPACE = 88.dp

/** One treatment in the list (P6 SPEC §4.3.3). */
data class TreatmentRow(
    val id: TreatmentId,
    val name: String,
    /** The medicines' names, shown for an Active treatment. */
    val medicines: List<String>,
    /** An Upcoming treatment's first take. */
    val startsAt: LocalDateTime? = null,
    /** When a Finished treatment was ended early, or `null`. */
    val endedAt: LocalDateTime? = null,
    /** A Finished treatment's last take, when it wasn't ended early. */
    val lastTake: LocalDateTime? = null,
)

/** What the Treatments screen shows. `null` while the stored data hasn't been read. */
sealed interface TreatmentsState {
    data object Unreadable : TreatmentsState

    /** Every treatment, in its group, in the group's order (P6 SPEC §4.3.3). */
    data class Ready(
        val active: List<TreatmentRow>,
        val upcoming: List<TreatmentRow>,
        val finished: List<TreatmentRow>,
        val today: LocalDate,
    ) : TreatmentsState {
        val isEmpty: Boolean get() = active.isEmpty() && upcoming.isEmpty() && finished.isEmpty()
    }
}

/** Every treatment, grouped as Active, Upcoming and Finished (TR-1, TR-12). Stateless: it draws [state]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentsContent(
    state: TreatmentsState?,
    onBack: () -> Unit,
    onOpen: (TreatmentId) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.treatments_title)) }, navigationIcon = { BackButton(onBack) })
        },
        floatingActionButton = {
            if (state is TreatmentsState.Ready) {
                // The content overload: the one with `text` and `icon` hides its label from TalkBack (Material 3 1.4
                // wraps it in clearAndSetSemantics), so the button would have no name.
                ExtendedFloatingActionButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.treatments_new))
                }
            }
        },
    ) { padding ->
        when (state) {
            null -> Unit
            TreatmentsState.Unreadable -> UnreadableMessage(Modifier.padding(padding))
            is TreatmentsState.Ready ->
                if (state.isEmpty) {
                    EmptyTreatments(Modifier.padding(padding))
                } else {
                    TreatmentList(state, onOpen, padding)
                }
        }
    }
}

@Composable
private fun TreatmentList(
    state: TreatmentsState.Ready,
    onOpen: (TreatmentId) -> Unit,
    padding: PaddingValues,
) {
    val text = rememberDateTimeText()
    val direction = LocalLayoutDirection.current
    val groups =
        listOf(
            R.string.status_active to state.active,
            R.string.status_upcoming to state.upcoming,
            R.string.status_finished to state.finished,
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TREATMENTS_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = padding.calculateStartPadding(direction),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction),
                bottom = padding.calculateBottomPadding() + FAB_SPACE,
            ),
    ) {
        for ((heading, rows) in groups) {
            if (rows.isEmpty()) continue
            item(key = "heading-$heading") {
                Text(
                    text = stringResource(heading),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(rows, key = { it.id.value }) { row ->
                ListItem(
                    headlineContent = { Text(row.name) },
                    supportingContent = { Text(rowLine(row, text, state.today)) },
                    modifier = Modifier.clickable { onOpen(row.id) },
                )
            }
        }
    }
}

/** The line under a treatment's name: its medicines, when it starts, or when it ended. */
@Composable
private fun rowLine(
    row: TreatmentRow,
    text: DateTimeText,
    today: LocalDate,
): String =
    when {
        row.startsAt != null -> stringResource(R.string.treatments_starts, text.dateTime(row.startsAt, today))
        row.endedAt != null -> stringResource(R.string.treatments_ended, text.dateTime(row.endedAt, today))
        row.lastTake != null -> stringResource(R.string.treatments_finished, text.day(row.lastTake.toLocalDate(), today))
        row.medicines.isNotEmpty() -> row.medicines.joinToString(", ")
        else -> ""
    }

@Composable
private fun EmptyTreatments(modifier: Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.treatments_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.treatments_empty_text), style = MaterialTheme.typography.bodyMedium)
    }
}
