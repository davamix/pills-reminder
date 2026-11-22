package io.github.davamix.pillsreminder.check

import android.app.NotificationManager

/** The app's Reminders channel, as the check reads it (P7 SPEC §4.3.7, Q3). */
enum class ChannelState {
    /** High importance or more: reminders pop up and sound. Also while the channel doesn't exist yet. */
    ON,

    /** On, but below high importance: no pop-up, and no sound below default (HyperOS's category "Floating notifications" off). */
    NO_POP_UP,

    /** Turned off: no reminder appears. */
    OFF,
}

/** HyperOS's settings the app can't read, which the user confirms (P7 SPEC §4.3.7, Q4, T1). */
enum class HyperOsStep { AUTOSTART, FLOATING_NOTIFICATIONS, LOCK_SCREEN_NOTIFICATIONS }

/** One line of the check, in the order the screen shows them; HyperOS's steps carry their [step]. */
enum class CheckItem(
    val step: HyperOsStep? = null,
) {
    NOTIFICATIONS,
    EXACT_ALARMS,
    BATTERY,
    AUTOSTART(HyperOsStep.AUTOSTART),
    FLOATING_NOTIFICATIONS(HyperOsStep.FLOATING_NOTIFICATIONS),
    LOCK_SCREEN_NOTIFICATIONS(HyperOsStep.LOCK_SCREEN_NOTIFICATIONS),
}

/** A line's status: Android's items are OK or MISSING; HyperOS's steps TO_CONFIRM or CONFIRMED. */
enum class LineStatus { OK, MISSING, TO_CONFIRM, CONFIRMED }

/** Why the Notifications line is missing. */
enum class NotificationsProblem {
    /** The notification permission isn't granted. */
    NOT_ALLOWED,

    /** The Reminders channel is off. */
    CHANNEL_OFF,

    /** The Reminders channel doesn't pop up or sound. */
    NO_POP_UP,
}

/** One line of the check. [problem] says why the Notifications line is MISSING; `null` on every other line. */
data class CheckLine(
    val item: CheckItem,
    val status: LineStatus,
    val problem: NotificationsProblem? = null,
)

/** The reminder check (RM-9): its lines, and whether HyperOS's steps apply on this phone. */
data class ReminderCheck(
    val lines: List<CheckLine>,
    val hyperOs: Boolean,
) {
    /** What Home warns about: lines MISSING or TO_CONFIRM (P7 SPEC §4.3.6). */
    val toCheck: Int get() = lines.count { it.status == LineStatus.MISSING || it.status == LineStatus.TO_CONFIRM }
}

/**
 * The check (RM-9, P7 SPEC §4.3.7): Notifications, Exact alarms and Battery optimisation always, then HyperOS's steps on
 * a HyperOS phone. Android's items are OK or MISSING from [device]; HyperOS's steps CONFIRMED once in [record].
 */
fun reminderCheck(
    device: DeviceChecks,
    record: CheckRecord,
): ReminderCheck {
    val notifications =
        when {
            !device.notificationsAllowed -> CheckLine(CheckItem.NOTIFICATIONS, LineStatus.MISSING, NotificationsProblem.NOT_ALLOWED)
            device.channel == ChannelState.OFF -> CheckLine(CheckItem.NOTIFICATIONS, LineStatus.MISSING, NotificationsProblem.CHANNEL_OFF)
            device.channel == ChannelState.NO_POP_UP ->
                CheckLine(
                    CheckItem.NOTIFICATIONS,
                    LineStatus.MISSING,
                    NotificationsProblem.NO_POP_UP,
                )
            else -> CheckLine(CheckItem.NOTIFICATIONS, LineStatus.OK)
        }
    val android =
        listOf(
            notifications,
            CheckLine(CheckItem.EXACT_ALARMS, if (device.exactAlarms) LineStatus.OK else LineStatus.MISSING),
            CheckLine(CheckItem.BATTERY, if (device.batteryOptimised) LineStatus.MISSING else LineStatus.OK),
        )
    val steps =
        if (!device.hyperOs) {
            emptyList()
        } else {
            CheckItem.entries.mapNotNull { item ->
                item.step?.let { step -> CheckLine(item, if (step in record.confirmed) LineStatus.CONFIRMED else LineStatus.TO_CONFIRM) }
            }
        }
    return ReminderCheck(android + steps, device.hyperOs)
}

/**
 * The Reminders channel's state from its importance: `null` while it doesn't exist yet, which `Reminders.start()`
 * creates at every start, counts as on.
 */
fun channelStateOf(importance: Int?): ChannelState =
    when {
        importance == null -> ChannelState.ON
        importance == NotificationManager.IMPORTANCE_NONE -> ChannelState.OFF
        importance < NotificationManager.IMPORTANCE_HIGH -> ChannelState.NO_POP_UP
        else -> ChannelState.ON
    }

/** Whether HyperOS's steps apply: a Xiaomi phone (T1 C6: the phone says "Xiaomi", the emulator "Google"). */
fun isHyperOs(manufacturer: String): Boolean = manufacturer.equals("Xiaomi", ignoreCase = true)

/** A settings screen the check opens (P7 SPEC §4.3.8, as T1 measured them). */
enum class SettingsScreen {
    /** The app's notification settings; on HyperOS also its Floating and Lock screen notifications. */
    APP_NOTIFICATIONS,

    /** The Reminders channel's settings. */
    CHANNEL,

    /** The app's details, which HyperOS redirects to its own App info. */
    APP_DETAILS,

    /** Android's battery optimisation dialog, asked of `com.android.settings`. */
    BATTERY_DIALOG,

    /** The same request to any app that takes it: on HyperOS, a chooser of its battery saver pages. */
    BATTERY_ANY,

    /** HyperOS's Autostart list, where the app is one row. */
    AUTOSTART_LIST,
}

/** The screens to try for [item], in order: the first that opens wins (P7 SPEC §4.3.8). */
fun settingsScreens(
    item: CheckItem,
    device: DeviceChecks,
): List<SettingsScreen> =
    when (item) {
        CheckItem.NOTIFICATIONS ->
            if (device.notificationsAllowed && device.channel != ChannelState.ON) {
                listOf(SettingsScreen.CHANNEL, SettingsScreen.APP_NOTIFICATIONS, SettingsScreen.APP_DETAILS)
            } else {
                listOf(SettingsScreen.APP_NOTIFICATIONS, SettingsScreen.APP_DETAILS)
            }
        // Not "Alarms & reminders": it shows a disabled switch, off, for an app with USE_EXACT_ALARM (T1 C1).
        CheckItem.EXACT_ALARMS -> listOf(SettingsScreen.APP_DETAILS)
        CheckItem.BATTERY -> listOf(SettingsScreen.BATTERY_DIALOG, SettingsScreen.BATTERY_ANY)
        CheckItem.AUTOSTART -> listOf(SettingsScreen.AUTOSTART_LIST, SettingsScreen.APP_DETAILS)
        CheckItem.FLOATING_NOTIFICATIONS, CheckItem.LOCK_SCREEN_NOTIFICATIONS ->
            listOf(SettingsScreen.APP_NOTIFICATIONS, SettingsScreen.APP_DETAILS)
    }
