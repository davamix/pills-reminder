package io.github.davamix.pillsreminder.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.davamix.pillsreminder.ui.theme.PillsReminderTheme

/**
 * The debug tools (D-15, P5 Q3): a launcher icon of their own, "Pills debug", in debug builds only. Edge to edge like
 * `MainActivity` (P1-D4).
 */
class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PillsReminderTheme {
                DebugScreen()
            }
        }
    }
}
