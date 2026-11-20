package io.github.davamix.pillsreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.davamix.pillsreminder.navigation.AppNavHost
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
}
