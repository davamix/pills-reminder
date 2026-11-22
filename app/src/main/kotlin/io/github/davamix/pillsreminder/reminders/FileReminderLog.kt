package io.github.davamix.pillsreminder.reminders

import android.util.Log
import java.io.File
import java.io.IOException
import java.time.Clock

/**
 * The debug builds' reminder log (P5-D9, P5 SPEC §4.3.10): to logcat, tag `PillsReminders`, and appended to [file] in
 * the app's private storage, so a run of hours can be read afterwards. Trimmed to its newer half past 1 MB. Release
 * builds use [ReminderLog.None]: `AppContainer` chooses.
 */
class FileReminderLog(
    private val file: File,
    private val clock: Clock,
) : ReminderLog {
    override fun event(line: String) {
        Log.i(TAG, line)
        synchronized(this) {
            try {
                file.appendText("${clock.instant()} $line\n")
                if (file.length() > MAX_BYTES) {
                    val lines = file.readLines()
                    file.writeText(lines.drop(lines.size / 2).joinToString("\n", postfix = "\n"))
                }
            } catch (e: IOException) {
                Log.w(TAG, "The log file can't be written: ${e::class.simpleName}")
            }
        }
    }

    companion object {
        /** The file's name in the app's `files` folder; the debug screen shows its last lines. */
        const val FILE_NAME = "reminders-log.txt"
        private const val TAG = "PillsReminders"
        private const val MAX_BYTES = 1_000_000L
    }
}
