package io.github.davamix.pillsreminder

import android.app.Application
import io.github.davamix.pillsreminder.di.AppContainer

class PillsReminderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
