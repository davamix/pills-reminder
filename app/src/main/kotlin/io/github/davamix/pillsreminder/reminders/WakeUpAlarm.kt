package io.github.davamix.pillsreminder.reminders

import java.time.Instant

/**
 * The app's single alarm (D-7): an alarm clock on Android (D-8, `AlarmClockWakeUp` in T5); a fake in tests. Setting it
 * again replaces it.
 */
interface WakeUpAlarm {
    /** Sets the alarm at [at]. [retry]: it tries again after a failed write (Q1), and its receiver says so in the log. */
    fun set(
        at: Instant,
        retry: Boolean = false,
    )

    fun cancel()
}
