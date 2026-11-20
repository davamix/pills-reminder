package io.github.davamix.pillsreminder.core.reminders

import java.time.Duration

/** Works out what the notification shade should hold at a given moment (plan §2.3, D-7). Pure: no clock, no state. */
object ReminderPlanner {
    /** How long a snooze lasts (RM-3). */
    val SNOOZE: Duration = Duration.ofMinutes(10)

    /** The time between two alerts of a round (RM-5). */
    val REPEAT_AFTER: Duration = Duration.ofMinutes(10)

    /** The alerts in a round: the first, then up to 3 repeats (RM-5). */
    const val ALERTS_PER_ROUND: Int = 4
}
