package io.github.davamix.pillsreminder.check

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.github.davamix.pillsreminder.reminders.ReminderChannel

/** What the reminder check reads from the device (RM-9, P7 SPEC §4.2). */
data class DeviceChecks(
    /** `NotificationManager.areNotificationsEnabled()`: the notification permission. */
    val notificationsAllowed: Boolean,
    /** The Reminders channel (Q3). */
    val channel: ChannelState,
    /** `AlarmManager.canScheduleExactAlarms()`. Always true with `USE_EXACT_ALARM` (D-8, T1 C5). */
    val exactAlarms: Boolean,
    /** Android's battery optimisation is on for the app: the default. */
    val batteryOptimised: Boolean,
    /** A Xiaomi phone, so HyperOS's steps apply (Q4, T1 C6). */
    val hyperOs: Boolean,
)

/** Reads what reminders depend on, and opens the settings screen of a check's item. Fakes in tests (P7-D2). */
interface ReminderCheckDevice {
    /** What the device says now: a few calls to system services, quick enough for the main thread. */
    fun read(): DeviceChecks

    /** Opens [item]'s settings screen, or Android's dialog for battery optimisation, from [activity] (P7 SPEC §4.3.8). */
    fun open(
        activity: Context,
        item: CheckItem,
    )
}

/** [ReminderCheckDevice] on Android's services, with the screens T1 measured (P7 SPEC §4.3.8, §4.6). */
class AndroidReminderCheckDevice(
    context: Context,
) : ReminderCheckDevice {
    private val context = context.applicationContext
    private val packageName = this.context.packageName

    override fun read(): DeviceChecks {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return DeviceChecks(
            notificationsAllowed = notifications.areNotificationsEnabled(),
            channel = channelStateOf(notifications.getNotificationChannel(ReminderChannel.ID)?.importance),
            exactAlarms = alarms.canScheduleExactAlarms(),
            batteryOptimised = !power.isIgnoringBatteryOptimizations(packageName),
            hyperOs = isHyperOs(Build.MANUFACTURER),
        )
    }

    /**
     * Starts the first of the item's screens that opens. It doesn't ask `resolveActivity()` first: package visibility
     * doesn't promise it for other apps' screens without `<queries>` (P7 SPEC §4.3.8).
     */
    override fun open(
        activity: Context,
        item: CheckItem,
    ) {
        for (screen in settingsScreens(item, read())) {
            try {
                activity.startActivity(intentOf(screen))
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
    }

    // The permission is declared for this check: plan §3 allows it (RM-9).
    @SuppressLint("BatteryLife")
    private fun intentOf(screen: SettingsScreen): Intent =
        when (screen) {
            SettingsScreen.APP_NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            SettingsScreen.CHANNEL ->
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderChannel.ID)
            SettingsScreen.APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())
            // Without the package, HyperOS offers a chooser of its own battery saver pages (T1 C1).
            SettingsScreen.BATTERY_DIALOG ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()).setPackage(SETTINGS_PACKAGE)
            SettingsScreen.BATTERY_ANY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())
            SettingsScreen.AUTOSTART_LIST -> Intent().setComponent(AUTOSTART_LIST)
        }

    private fun packageUri(): Uri = Uri.fromParts("package", packageName, null)

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"

        /** HyperOS's "Autoinicio en segundo plano" list (T1 C1). */
        val AUTOSTART_LIST = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    }
}
