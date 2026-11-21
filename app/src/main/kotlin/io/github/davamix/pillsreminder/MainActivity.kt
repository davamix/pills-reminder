package io.github.davamix.pillsreminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.davamix.pillsreminder.navigation.AppNavHost
import io.github.davamix.pillsreminder.navigation.TakeRoute
import io.github.davamix.pillsreminder.reminders.ReconcileTrigger
import io.github.davamix.pillsreminder.reminders.ReminderIntents
import io.github.davamix.pillsreminder.ui.theme.PillsReminderTheme

class MainActivity : ComponentActivity() {
    /** The take a tapped reminder opens, until [AppNavHost] has opened it (RM-6, P5 SPEC §4.3.8). */
    private var takeToOpen by mutableStateOf<TakeRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge on every Android version, so the phone (API 33) and the emulator (API 36), where Android forces
        // it, look the same. Screens keep their content out of the system bars through Scaffold (P1-D4).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a new start: a recreated activity has opened it already, and one relaunched from Recents gets the
        // intent of the tap that started its task, maybe long ago.
        if (savedInstanceState == null && !launchedFromHistory(intent)) takeToOpen = takeOf(intent)
        setContent {
            PillsReminderTheme {
                AppNavHost(openTake = takeToOpen, onTakeOpened = { takeToOpen = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A reminder tapped while the activity exists. Any other intent leaves the app on the screen it was on.
        takeOf(intent)?.let { takeToOpen = it }
    }

    override fun onStart() {
        super.onStart()
        // The app comes to the front: for example after the notification permission was granted, the reminders the plan
        // holds are posted, silently (P5 SPEC §4.3.2).
        val reminders = (application as PillsReminderApp).container.reminders
        reminders.runAsync(done = {}) { reminders.reconcile(ReconcileTrigger.APP_OPEN) }
    }
}

private fun launchedFromHistory(intent: Intent) = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

/** The take a reminder's intent opens, or `null` for any other intent. */
internal fun takeOf(intent: Intent): TakeRoute? =
    ReminderIntents.keyOf(intent)?.let { TakeRoute(medicineId = it.medicineId.value, plannedAt = it.plannedAt.toString()) }
