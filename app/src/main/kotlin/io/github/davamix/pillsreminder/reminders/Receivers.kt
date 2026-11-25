package io.github.davamix.pillsreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.davamix.pillsreminder.PillsReminderApp

// The three receivers (P5 SPEC §4.3.6), none exported. Each does its work through Reminders.runAsync, inside goAsync(),
// so the process stays alive until the shade and the alarm are right, and finishes however the work ends.

private val Context.reminders: Reminders get() = (applicationContext as PillsReminderApp).container.reminders

/** The single alarm fired: a take falls due, a repeat or a snooze end comes round, or a failed write is retried. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!ReminderIntents.isWakeUp(intent)) return
        val trigger = if (ReminderIntents.isRetry(intent)) ReconcileTrigger.RETRY else ReconcileTrigger.ALARM
        val reminders = context.reminders
        val pending = goAsync()
        // The broadcast, and the alarm's wake lock with it, lasts until an alert has played (P8).
        reminders.runAsync(done = pending::finish) { reminders.reconcileAndSettle(trigger) }
    }
}

/** Taken, Snooze or Skip on a reminder (RM-3, §4.3.5). */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = ReminderIntents.actionOf(intent) ?: return
        val reminders = context.reminders
        val key = ReminderIntents.keyOf(intent)
        val pending = goAsync()
        reminders.runAsync(done = pending::finish) {
            if (key != null) reminders.answer(action, key) else reminders.reconcile(ReconcileTrigger.ACTION)
        }
    }
}

/** The system's events that can lose an alarm or move the clock (D-9, NF-2). */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val trigger =
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> ReconcileTrigger.BOOT
                Intent.ACTION_MY_PACKAGE_REPLACED -> ReconcileTrigger.PACKAGE_REPLACED
                Intent.ACTION_TIME_CHANGED -> ReconcileTrigger.TIME_SET
                Intent.ACTION_TIMEZONE_CHANGED -> ReconcileTrigger.TIMEZONE_CHANGED
                else -> return
            }
        val reminders = context.reminders
        val pending = goAsync()
        reminders.runAsync(done = pending::finish) { reminders.reconcileAndSettle(trigger) }
    }
}
