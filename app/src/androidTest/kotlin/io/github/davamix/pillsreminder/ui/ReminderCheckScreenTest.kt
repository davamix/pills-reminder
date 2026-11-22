package io.github.davamix.pillsreminder.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.check.ChannelState
import io.github.davamix.pillsreminder.check.CheckItem
import io.github.davamix.pillsreminder.check.CheckRecord
import io.github.davamix.pillsreminder.check.DeviceChecks
import io.github.davamix.pillsreminder.check.HyperOsStep
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.data.DeviceExample
import io.github.davamix.pillsreminder.ui.settings.SETTINGS_LIST_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reminder check in Settings, and Home's warning with it (RM-9, P7 SPEC §4.3.6–§4.3.10). Fakes only: the fake device
 * records what a button opens and opens nothing, and says notifications are allowed, so Android's dialog never shows.
 */
@RunWith(AndroidJUnit4::class)
class ReminderCheckScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = nov(23, 10, 30)

    /** Everything missing, on a HyperOS phone with no step confirmed: six lines to check. */
    private val nothingSet =
        DeviceChecks(notificationsAllowed = false, channel = ChannelState.ON, exactAlarms = false, batteryOptimised = true, hyperOs = true)

    private fun app(
        checks: DeviceChecks,
        record: CheckRecord = CheckRecord(firstLaunchDone = true),
    ) = FakeApp(PillsData(listOf(DeviceExample.treatment)), now, record).also { it.checkDevice.checks = checks }

    private fun assertShown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun openSettings() = compose.onNodeWithContentDescription(text(R.string.home_settings)).performClick()

    /**
     * The node matching [matcher] right under [item]'s title: its own button or Done box. The list is scrolled to the
     * line first, which puts it at the top.
     */
    private fun under(
        item: CheckItem,
        title: Int,
        matcher: SemanticsMatcher,
    ): SemanticsNodeInteraction {
        compose.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToKey(item.name)
        val below =
            compose
                .onNodeWithText(text(title))
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val node: SemanticsNode =
            compose
                .onAllNodes(matcher)
                .fetchSemanticsNodes()
                .filter { it.boundsInRoot.top >= below }
                .minBy { it.boundsInRoot.top }
        return compose.onNode(matcher and SemanticsMatcher("node ${node.id}") { it.id == node.id })
    }

    private fun button(label: Int) = hasText(text(label)) and hasClickAction()

    private val done = hasText(text(R.string.settings_done)) and isToggleable()

    /** As when the user comes back from Android's settings: the activity is paused, then resumed. */
    private fun leaveAndComeBack() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    @Test
    fun `each line says what is missing and its button opens its own settings`() {
        val app = app(nothingSet)
        compose.showApp(app)
        assertShown(quantity(R.plurals.home_check_warning, 6))
        openSettings()

        assertShown(quantity(R.plurals.settings_check_to_check, 6))
        val lines =
            listOf(
                Triple(CheckItem.NOTIFICATIONS, R.string.settings_notifications, R.string.settings_open),
                Triple(CheckItem.EXACT_ALARMS, R.string.settings_exact_alarms, R.string.settings_open),
                Triple(CheckItem.BATTERY, R.string.settings_battery, R.string.settings_turn_off),
                Triple(CheckItem.AUTOSTART, R.string.settings_autostart, R.string.settings_open),
                Triple(CheckItem.FLOATING_NOTIFICATIONS, R.string.settings_floating, R.string.settings_open),
                Triple(CheckItem.LOCK_SCREEN_NOTIFICATIONS, R.string.settings_lock_screen, R.string.settings_open),
            )
        for ((item, title, label) in lines) {
            under(item, title, button(label)).assertTouchHeightIsAtLeast(48.dp).performClick()
            compose.waitForIdle()
        }

        assertEquals(lines.map { it.first }, app.checkDevice.opened)
        compose.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToKey(CheckItem.NOTIFICATIONS.name)
        assertShown(text(R.string.settings_notifications_not_allowed))
        assertShown(text(R.string.settings_exact_alarms_missing))
        compose.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToKey(CheckItem.BATTERY.name)
        assertShown(text(R.string.settings_battery_missing))
        assertShown(text(R.string.settings_battery_hyperos))
    }

    @Test
    fun `a change made in Android settings shows when Settings and then Home are resumed`() {
        val app = app(allSet.copy(batteryOptimised = true))
        compose.showApp(app)
        assertShown(quantity(R.plurals.home_check_warning, 1))
        compose.tap(R.string.home_check_open)
        assertShown(text(R.string.settings_battery_missing))

        app.checkDevice.checks = allSet
        leaveAndComeBack()

        assertShown(text(R.string.settings_check_all_set))
        assertShown(text(R.string.settings_battery_ok))
        pressBack()
        assertShown(text(R.string.home_title))
        compose.onNodeWithText(quantity(R.plurals.home_check_warning, 1)).assertDoesNotExist()
    }

    @Test
    fun `Done confirms a step and the warning on Home goes`() {
        val confirmed = setOf(HyperOsStep.AUTOSTART, HyperOsStep.FLOATING_NOTIFICATIONS)
        val app = app(allSet.copy(hyperOs = true), CheckRecord(firstLaunchDone = true, confirmed = confirmed))
        compose.showApp(app)
        assertShown(quantity(R.plurals.home_check_warning, 1))
        compose.tap(R.string.home_check_open)

        under(CheckItem.AUTOSTART, R.string.settings_autostart, done).assertIsOn()
        under(CheckItem.LOCK_SCREEN_NOTIFICATIONS, R.string.settings_lock_screen, done)
            .assertIsOff()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.waitUntil { HyperOsStep.LOCK_SCREEN_NOTIFICATIONS in app.checkStore.record.value.confirmed }
        under(CheckItem.LOCK_SCREEN_NOTIFICATIONS, R.string.settings_lock_screen, done).assertIsOn()

        pressBack()
        assertShown(text(R.string.home_title))
        compose.onNodeWithText(quantity(R.plurals.home_check_warning, 1)).assertDoesNotExist()
        assertEquals(HyperOsStep.entries.toSet(), app.checkStore.record.value.confirmed)
    }

    @Test
    fun `at the first launch Home opens the check once and Back returns to Home`() {
        val app = app(allSet, CheckRecord())
        compose.showApp(app)

        assertShown(text(R.string.settings_title))
        assertShown(text(R.string.settings_check_all_set))
        compose.waitUntil { app.checkStore.record.value.firstLaunchDone }

        pressBack()
        assertShown(text(R.string.home_title))
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.settings_title)).assertDoesNotExist()
        // Notifications are allowed: no dialog, so nothing to reconcile.
        assertEquals(0, app.reconciles)
    }
}
