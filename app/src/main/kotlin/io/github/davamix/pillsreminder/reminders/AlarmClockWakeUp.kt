package io.github.davamix.pillsreminder.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import java.time.Instant

/**
 * The single alarm as an alarm clock (D-8, P5 SPEC §4.3.3): HyperOS holds exact alarms for up to 5 minutes with the
 * screen off, and never holds an alarm clock (T1). Its action tells it apart: instrumented tests use one of their own,
 * so they never replace the app's real alarm (P5-D11).
 */
class AlarmClockWakeUp(
    context: Context,
    private val action: String = ReminderIntents.ACTION_WAKE_UP,
    private val log: ReminderLog = ReminderLog.None,
) : WakeUpAlarm {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)

    // USE_EXACT_ALARM, granted at install, covers alarm clocks; Lint looks only for SCHEDULE_EXACT_ALARM. The check of
    // canScheduleExactAlarms() keeps a receiver from crashing should it ever be missing.
    @SuppressLint("MissingPermission")
    override fun set(
        at: Instant,
        retry: Boolean,
    ) {
        val operation = checkNotNull(ReminderIntents.wakeUp(context, action, retry))
        if (alarms.canScheduleExactAlarms()) {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), ReminderIntents.openApp(context)), operation)
        } else {
            // Late, rather than not at all; P7's check shows the permission as missing.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
            log.event("alarm inexact: exact alarms not allowed")
        }
    }

    override fun cancel() {
        val operation =
            ReminderIntents.wakeUp(context, action, flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) ?: return
        alarms.cancel(operation)
        operation.cancel()
    }
}
