package io.github.davamix.pillsreminder.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.navigation.AppNavHost
import io.github.davamix.pillsreminder.navigation.TakeRoute
import io.github.davamix.pillsreminder.ui.common.DateTimeText
import io.github.davamix.pillsreminder.ui.common.DateTimeWords
import io.github.davamix.pillsreminder.ui.common.ScreenDependencies
import io.github.davamix.pillsreminder.ui.theme.PillsReminderTheme
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

// Helpers for P6's UI flow tests. They run the app's real navigation graph with an in-memory repository and a fixed
// clock (P6-D2), so they never touch the app's data or its reminders, on the phone too. The repo is public: the data is
// the spec's §4.4 example.

val madrid: ZoneId = ZoneId.of("Europe/Madrid")

fun nov(
    day: Int,
    hour: Int,
    minute: Int = 0,
): LocalDateTime = LocalDateTime.of(2026, 11, day, hour, minute)

fun clockAt(local: LocalDateTime): Clock = Clock.fixed(local.atZone(madrid).toInstant(), madrid)

/** The app's navigation graph on [data] at [now], with no ticks: the clock doesn't move. */
class FakeApp(
    data: PillsData = PillsData(),
    now: LocalDateTime,
) {
    val repository = InMemoryTreatmentRepository(StoredData.Readable(data))
    val dependencies = ScreenDependencies(repository, clockAt(now), allowMinutes = true, minuteTicks = flowOf(Unit))

    /** The data as it is now. */
    val data: PillsData get() = (repository.data.value as StoredData.Readable).data
}

/** Shows the app's navigation graph with [app]'s fakes, opening [openTake] when it is set, as a tapped reminder does. */
fun ComposeContentTestRule.showApp(
    app: FakeApp,
    openTake: () -> TakeRoute? = { null },
    onTakeOpened: () -> Unit = {},
) {
    setContent { AppUnderTest(app, openTake(), onTakeOpened) }
}

@Composable
private fun AppUnderTest(
    app: FakeApp,
    openTake: TakeRoute?,
    onTakeOpened: () -> Unit,
) {
    PillsReminderTheme {
        AppNavHost(
            navController = rememberNavController(),
            openTake = openTake,
            onTakeOpened = onTakeOpened,
            dependencies = app.dependencies,
        )
    }
}

val context: Context get() = ApplicationProvider.getApplicationContext()

/** A string resource; with no [args] it is read as it is, so a pattern such as `%1$s %2$s` isn't formatted. */
fun text(
    @StringRes id: Int,
    vararg args: Any,
): String = if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

/** Dates and times as the app writes them on this device: its 24-hour setting, the UI's words. */
fun dateTimeText(): DateTimeText =
    DateTimeText(
        Locale.forLanguageTag(text(R.string.date_locale)),
        DateFormat.is24HourFormat(context),
        DateTimeWords(
            today = text(R.string.day_today),
            tomorrow = text(R.string.day_tomorrow),
            yesterday = text(R.string.day_yesterday),
            dayAndTime = text(R.string.day_and_time),
            datePattern = text(R.string.date_pattern),
            dateWithYearPattern = text(R.string.date_pattern_with_year),
        ),
    )

fun ComposeContentTestRule.tap(
    @StringRes id: Int,
) = onNodeWithText(text(id)).performScrollToIfNeeded().performClick()

fun ComposeContentTestRule.tapText(value: String) = onNodeWithText(value).performScrollToIfNeeded().performClick()

fun ComposeContentTestRule.tapBack() = onNodeWithContentDescription(text(R.string.navigate_back)).performClick()

/**
 * Picks [day] of the month shown in the open date picker, then OK. A day cell's text is its whole date, such as
 * "Monday, November 23, 2026", in the picker's locale; only that cell holds the day's number and can be clicked.
 */
fun ComposeContentTestRule.pickDay(day: Int) {
    onNode(hasText(day.toString(), substring = true) and hasClickAction()).performClick()
    tap(R.string.action_ok)
}

/** Types [value] into the text field labelled [label], replacing what it held. */
fun ComposeContentTestRule.type(
    @StringRes label: Int,
    value: String,
) = onNode(hasSetTextAction() and hasText(text(label))).performScrollToIfNeeded().performTextReplacement(value)

/** Scrolls to the node when it is in a scrolling column; a node outside one is left as it is. */
fun SemanticsNodeInteraction.performScrollToIfNeeded(): SemanticsNodeInteraction =
    try {
        performScrollTo()
    } catch (e: AssertionError) {
        this
    }
