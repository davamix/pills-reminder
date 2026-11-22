package io.github.davamix.pillsreminder.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.PluralsRes
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.check.ChannelState
import io.github.davamix.pillsreminder.check.CheckItem
import io.github.davamix.pillsreminder.check.CheckRecord
import io.github.davamix.pillsreminder.check.DeviceChecks
import io.github.davamix.pillsreminder.check.InMemoryCheckStore
import io.github.davamix.pillsreminder.check.ReminderCheckDevice
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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

/**
 * The app's navigation graph on [data] at [now], with no ticks: the clock doesn't move. The reminder check reads
 * [checkDevice] and keeps [checkStore]: by default everything is set, on a phone that isn't HyperOS, and the first
 * launch is done, so Home shows no warning and opens no check (P7). [unreadable]: the stored data can't be read.
 */
class FakeApp(
    data: PillsData = PillsData(),
    now: LocalDateTime,
    record: CheckRecord = CheckRecord(firstLaunchDone = true),
    unreadable: Boolean = false,
) {
    val repository = InMemoryTreatmentRepository(if (unreadable) StoredData.Unreadable else StoredData.Readable(data))
    val checkDevice = FakeCheckDevice()
    val checkStore = InMemoryCheckStore(record)
    private val reconciled = AtomicInteger()

    /** How many times the screens asked the reminders to reconcile (P7-D6). */
    val reconciles: Int get() = reconciled.get()

    val dependencies =
        ScreenDependencies(
            repository,
            clockAt(now),
            allowMinutes = true,
            minuteTicks = flowOf(Unit),
            checkDevice = checkDevice,
            checkStore = checkStore,
            reconcile = { reconciled.incrementAndGet() },
        )

    /** The data as it is now. */
    val data: PillsData get() = (repository.data.value as StoredData.Readable).data
}

/** Everything reminders need is set, on a phone that isn't HyperOS: the check has nothing to warn about. */
val allSet =
    DeviceChecks(notificationsAllowed = true, channel = ChannelState.ON, exactAlarms = true, batteryOptimised = false, hyperOs = false)

/** The reminder check's device, as the test sets it (P7-D2): it records what it opened, and opens nothing. */
class FakeCheckDevice(
    @Volatile var checks: DeviceChecks = allSet,
) : ReminderCheckDevice {
    val opened: MutableList<CheckItem> = CopyOnWriteArrayList()

    override fun read(): DeviceChecks = checks

    override fun open(
        activity: Context,
        item: CheckItem,
    ) {
        opened += item
    }
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

/** A plural resource for [count], such as "1 setting to check." */
fun quantity(
    @PluralsRes id: Int,
    count: Int,
): String = context.resources.getQuantityString(id, count, count)

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

/**
 * Asserts that the node takes touches over at least [min] of height (NF-7). Material 3's buttons are drawn 40 dp tall
 * and take touches over 48 dp, so `assertHeightIsAtLeast`, which reads the drawn size, can't check them (P7 T6).
 */
fun SemanticsNodeInteraction.assertTouchHeightIsAtLeast(min: Dp): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    val height = with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() }
    if (height < min - 0.5.dp) throw AssertionError("Touch height is $height, expected at least $min")
    return this
}

/** Scrolls to the node when it is in a scrolling column; a node outside one is left as it is. */
fun SemanticsNodeInteraction.performScrollToIfNeeded(): SemanticsNodeInteraction =
    try {
        performScrollTo()
    } catch (e: AssertionError) {
        this
    }
