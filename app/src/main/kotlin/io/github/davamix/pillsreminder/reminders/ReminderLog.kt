package io.github.davamix.pillsreminder.reminders

/**
 * Reminder events for the debug log (P5-D9). A line holds only triggers, take keys as [TakeKeyCodec] writes them,
 * instants, counts and exception class names: never names, doses, instructions or notes, nor an exception's message
 * (plan §3). Release builds use [None]; debug builds write to logcat and to a file (T5).
 */
fun interface ReminderLog {
    fun event(line: String)

    companion object {
        /** Logs nothing: release builds and tests that don't look. */
        val None = ReminderLog {}
    }
}
