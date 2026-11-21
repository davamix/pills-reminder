package io.github.davamix.pillsreminder.reminders

import android.app.NotificationManager
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notification channel (D-10, P5 SPEC §4.3.4) as Android holds it. It reads the app's real channel: a setting the
 * user changed on the device shows here. Test names are sentences with no punctuation but `-` (P1-D7).
 */
@RunWith(AndroidJUnit4::class)
class ReminderChannelTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `the channel is heads-up with the default sound and without vibration or a Do Not Disturb bypass`() {
        ReminderChannel.create(context)

        val channel =
            checkNotNull(context.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderChannel.ID)) {
                "no channel"
            }
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, channel.sound)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.canBypassDnd())
        assertEquals("Reminders", channel.name)
    }
}
