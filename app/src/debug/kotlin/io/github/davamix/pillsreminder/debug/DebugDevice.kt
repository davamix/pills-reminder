package io.github.davamix.pillsreminder.debug

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import io.github.davamix.pillsreminder.reminders.AndroidReminderShade
import io.github.davamix.pillsreminder.reminders.FileReminderLog
import io.github.davamix.pillsreminder.reminders.ReminderChannel
import java.io.File
import java.time.Instant

/** What the debug screen reads from the device, beside the stored data (P5 SPEC §4.3.9). */
data class DeviceSnapshot(
    val notificationsAllowed: Boolean = false,
    val exactAlarms: Boolean = false,
    /** Android's battery optimisation is on for the app: the default, and HyperOS's "Battery saver". */
    val batteryOptimised: Boolean = true,
    /** The reminders channel's importance, or `null` while there is no channel. */
    val channelImportance: Int? = null,
    /** The app's reminders in the shade. */
    val inShade: Int = 0,
    /** The next alarm clock of any app: Android keeps only one list. */
    val nextAlarmClock: Instant? = null,
    val nextAlarmClockIsOurs: Boolean = false,
    /** The newest lines of the reminder log, oldest first. */
    val log: List<String> = emptyList(),
)

/** Reads the device for the debug screen, and clears the reminder log. */
interface DebugDevice {
    fun read(): DeviceSnapshot

    fun clearLog()
}

class AndroidDebugDevice(
    context: Context,
) : DebugDevice {
    private val context = context.applicationContext
    private val logFile = File(this.context.filesDir, FileReminderLog.FILE_NAME)

    override fun read(): DeviceSnapshot {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val next = alarms.nextAlarmClock
        return DeviceSnapshot(
            notificationsAllowed = notifications.areNotificationsEnabled(),
            exactAlarms = alarms.canScheduleExactAlarms(),
            batteryOptimised = !power.isIgnoringBatteryOptimizations(context.packageName),
            channelImportance = notifications.getNotificationChannel(ReminderChannel.ID)?.importance,
            inShade = notifications.activeNotifications.count { it.tag?.startsWith("${AndroidReminderShade.TAG_PREFIX}/") == true },
            nextAlarmClock = next?.triggerTime?.let(Instant::ofEpochMilli),
            nextAlarmClockIsOurs = next?.showIntent?.creatorPackage == context.packageName,
            log = if (logFile.exists()) logFile.readLines().takeLast(LOG_LINES) else emptyList(),
        )
    }

    override fun clearLog() {
        synchronized(this) { if (logFile.exists()) logFile.writeText("") }
    }

    private companion object {
        const val LOG_LINES = 20
    }
}
