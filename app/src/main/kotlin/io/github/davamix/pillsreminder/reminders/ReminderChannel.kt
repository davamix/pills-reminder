package io.github.davamix.pillsreminder.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.davamix.pillsreminder.R

/**
 * The one notification channel, "Reminders" (D-10, P5 SPEC §4.3.4): heads-up, the default notification sound, no
 * vibration, no Do Not Disturb bypass. Android keeps a channel's sound, vibration and importance as first created, and
 * the user's changes to them: changing them needs a new [ID], with the old channel deleted.
 */
object ReminderChannel {
    const val ID = "reminders"

    /** Creates it, or updates its name and description; changes nothing else once it exists. */
    fun create(context: Context) {
        val channel =
            NotificationChannel(ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminders_channel_description)
                enableVibration(false)
                setBypassDnd(false)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
