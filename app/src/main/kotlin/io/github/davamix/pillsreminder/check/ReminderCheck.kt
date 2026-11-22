package io.github.davamix.pillsreminder.check

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
