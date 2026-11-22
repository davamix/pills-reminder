package io.github.davamix.pillsreminder.check

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The reminder check's lines (RM-9, P7 SPEC §4.3.7), the channel's state, HyperOS, and the screens each line opens. */
class ReminderCheckTest {
    private val allSet =
        DeviceChecks(notificationsAllowed = true, channel = ChannelState.ON, exactAlarms = true, batteryOptimised = false, hyperOs = false)
    private val none = CheckRecord()

    private fun ReminderCheck.line(item: CheckItem): CheckLine = lines.single { it.item == item }

    @Test
    fun `everything set on stock Android gives three lines, all OK, and nothing to check`() {
        val check = reminderCheck(allSet, none)

        assertEquals(listOf(CheckItem.NOTIFICATIONS, CheckItem.EXACT_ALARMS, CheckItem.BATTERY), check.lines.map { it.item })
        assertTrue(check.lines.all { it.status == LineStatus.OK && it.problem == null })
        assertFalse(check.hyperOs)
        assertEquals(0, check.toCheck)
    }

    @Test
    fun `the notification permission refused is missing, whatever the channel says`() {
        val check = reminderCheck(allSet.copy(notificationsAllowed = false, channel = ChannelState.OFF), none)

        assertEquals(
            CheckLine(CheckItem.NOTIFICATIONS, LineStatus.MISSING, NotificationsProblem.NOT_ALLOWED),
            check.line(CheckItem.NOTIFICATIONS),
        )
        assertEquals(1, check.toCheck)
    }

    @Test
    fun `a Reminders channel turned off is missing, as the channel off`() {
        val check = reminderCheck(allSet.copy(channel = ChannelState.OFF), none)

        assertEquals(
            CheckLine(CheckItem.NOTIFICATIONS, LineStatus.MISSING, NotificationsProblem.CHANNEL_OFF),
            check.line(CheckItem.NOTIFICATIONS),
        )
    }

    @Test
    fun `a Reminders channel without pop-ups is missing, as no pop-up`() {
        val check = reminderCheck(allSet.copy(channel = ChannelState.NO_POP_UP), none)

        assertEquals(
            CheckLine(CheckItem.NOTIFICATIONS, LineStatus.MISSING, NotificationsProblem.NO_POP_UP),
            check.line(CheckItem.NOTIFICATIONS),
        )
    }

    @Test
    fun `exact alarms not allowed are missing`() {
        val check = reminderCheck(allSet.copy(exactAlarms = false), none)

        assertEquals(LineStatus.MISSING, check.line(CheckItem.EXACT_ALARMS).status)
        assertEquals(LineStatus.OK, check.line(CheckItem.BATTERY).status)
        assertEquals(1, check.toCheck)
    }

    @Test
    fun `battery optimisation on is missing, and off is OK`() {
        assertEquals(LineStatus.MISSING, reminderCheck(allSet.copy(batteryOptimised = true), none).line(CheckItem.BATTERY).status)
        assertEquals(LineStatus.OK, reminderCheck(allSet, none).line(CheckItem.BATTERY).status)
        assertEquals(LineStatus.OK, reminderCheck(allSet.copy(batteryOptimised = true), none).line(CheckItem.EXACT_ALARMS).status)
    }

    @Test
    fun `on HyperOS the three steps follow Android's lines, each to confirm`() {
        val check = reminderCheck(allSet.copy(hyperOs = true), none)

        assertEquals(
            listOf(
                CheckItem.NOTIFICATIONS,
                CheckItem.EXACT_ALARMS,
                CheckItem.BATTERY,
                CheckItem.AUTOSTART,
                CheckItem.FLOATING_NOTIFICATIONS,
                CheckItem.LOCK_SCREEN_NOTIFICATIONS,
            ),
            check.lines.map { it.item },
        )
        assertTrue(check.lines.drop(3).all { it.status == LineStatus.TO_CONFIRM && it.problem == null })
        assertTrue(check.hyperOs)
        assertEquals(3, check.toCheck)
    }

    @Test
    fun `a confirmed step is confirmed, and no longer to check`() {
        val record = CheckRecord(confirmed = setOf(HyperOsStep.AUTOSTART, HyperOsStep.LOCK_SCREEN_NOTIFICATIONS))
        val check = reminderCheck(allSet.copy(hyperOs = true), record)

        assertEquals(LineStatus.CONFIRMED, check.line(CheckItem.AUTOSTART).status)
        assertEquals(LineStatus.TO_CONFIRM, check.line(CheckItem.FLOATING_NOTIFICATIONS).status)
        assertEquals(LineStatus.CONFIRMED, check.line(CheckItem.LOCK_SCREEN_NOTIFICATIONS).status)
        assertEquals(1, check.toCheck)
    }

    @Test
    fun `HyperOS's steps don't show on another phone, even confirmed`() {
        val check = reminderCheck(allSet, CheckRecord(confirmed = HyperOsStep.entries.toSet()))

        assertEquals(3, check.lines.size)
        assertEquals(0, check.toCheck)
    }

    @Test
    fun `everything missing on HyperOS gives six to check`() {
        val device =
            DeviceChecks(
                notificationsAllowed = false,
                channel = ChannelState.OFF,
                exactAlarms = false,
                batteryOptimised = true,
                hyperOs = true,
            )

        assertEquals(6, reminderCheck(device, none).toCheck)
    }

    @Test
    fun `the channel's importance gives its state, and no channel yet counts as on`() {
        assertEquals(ChannelState.ON, channelStateOf(null))
        assertEquals(ChannelState.OFF, channelStateOf(0))
        assertEquals(ChannelState.NO_POP_UP, channelStateOf(1))
        assertEquals(ChannelState.NO_POP_UP, channelStateOf(2))
        assertEquals(ChannelState.NO_POP_UP, channelStateOf(3))
        assertEquals(ChannelState.ON, channelStateOf(4))
        assertEquals(ChannelState.ON, channelStateOf(5))
    }

    @Test
    fun `a Xiaomi phone is HyperOS, whatever the case, and others aren't`() {
        assertTrue(isHyperOs("Xiaomi"))
        assertTrue(isHyperOs("XIAOMI"))
        assertTrue(isHyperOs("xiaomi"))
        assertFalse(isHyperOs("Google"))
        assertFalse(isHyperOs("POCO"))
        assertFalse(isHyperOs(""))
    }

    @Test
    fun `Notifications opens the app's notification settings, or the channel's when only the channel is wrong`() {
        val appScreens = listOf(SettingsScreen.APP_NOTIFICATIONS, SettingsScreen.APP_DETAILS)
        val channelScreens = listOf(SettingsScreen.CHANNEL, SettingsScreen.APP_NOTIFICATIONS, SettingsScreen.APP_DETAILS)

        assertEquals(appScreens, settingsScreens(CheckItem.NOTIFICATIONS, allSet))
        assertEquals(
            appScreens,
            settingsScreens(CheckItem.NOTIFICATIONS, allSet.copy(notificationsAllowed = false, channel = ChannelState.OFF)),
        )
        assertEquals(channelScreens, settingsScreens(CheckItem.NOTIFICATIONS, allSet.copy(channel = ChannelState.OFF)))
        assertEquals(channelScreens, settingsScreens(CheckItem.NOTIFICATIONS, allSet.copy(channel = ChannelState.NO_POP_UP)))
    }

    @Test
    fun `each other line opens the screens T1 measured, in order`() {
        assertEquals(listOf(SettingsScreen.APP_DETAILS), settingsScreens(CheckItem.EXACT_ALARMS, allSet))
        assertEquals(listOf(SettingsScreen.BATTERY_DIALOG, SettingsScreen.BATTERY_ANY), settingsScreens(CheckItem.BATTERY, allSet))
        assertEquals(listOf(SettingsScreen.AUTOSTART_LIST, SettingsScreen.APP_DETAILS), settingsScreens(CheckItem.AUTOSTART, allSet))
        val notifications = listOf(SettingsScreen.APP_NOTIFICATIONS, SettingsScreen.APP_DETAILS)
        assertEquals(notifications, settingsScreens(CheckItem.FLOATING_NOTIFICATIONS, allSet))
        assertEquals(notifications, settingsScreens(CheckItem.LOCK_SCREEN_NOTIFICATIONS, allSet))
    }
}
