package io.github.davamix.pillsreminder

import android.app.Application
import io.github.davamix.pillsreminder.di.AppContainer

class PillsReminderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The channel, then a reconcile on the stored data and after each change (P5-D2). This also runs when the
        // system starts the process for a receiver, and sets the alarm again after a Force stop (NF-2).
        container.reminders.start()
    }
}
