package io.github.davamix.pillsreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.davamix.pillsreminder.navigation.AppNavHost
import io.github.davamix.pillsreminder.reminders.ReconcileTrigger
import io.github.davamix.pillsreminder.ui.theme.PillsReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge on every Android version, so the phone (API 33) and the emulator (API 36), where Android forces
        // it, look the same. Screens keep their content out of the system bars through Scaffold (P1-D4).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PillsReminderTheme {
                AppNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The app comes to the front: for example after the notification permission was granted, the reminders the plan
        // holds are posted, silently (P5 SPEC §4.3.2).
        val reminders = (application as PillsReminderApp).container.reminders
        reminders.runAsync(done = {}) { reminders.reconcile(ReconcileTrigger.APP_OPEN) }
    }
}
