package io.github.davamix.pillsreminder.reminders

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The alarm clock (D-8, P5 SPEC §4.3.3), read back from `dumpsys alarm`, with an action of its own so it never replaces
 * the app's real alarm (P5-D11). Its alarms are an hour ahead, and cancelled after each test.
 */
@RunWith(AndroidJUnit4::class)
class WakeUpAlarmTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val action = ReminderIntents.ACTION_WAKE_UP + ".TEST"
    private val alarm = AlarmClockWakeUp(context, action)

    private fun dumpsys(): List<String> {
        val output = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }.lines()
    }

    /** Every pending alarm whose tag is this test's action: each one's lines, from its `Alarm{…}` header on. */
    private fun blocks(): List<List<String>> {
        val lines = dumpsys()
        return lines.indices
            .filter { lines[it].trim() == "tag=*walarm*:$action" }
            .map { tag ->
                val start = (tag downTo 0).first { lines[it].contains(": Alarm{") }
                val end =
                    (tag + 1 until lines.size).firstOrNull { lines[it].contains(": Alarm{") || !lines[it].startsWith("      ") }
                        ?: lines.size
                lines.subList(start, end)
            }
    }

    /**
     * The one pending alarm with this test's action. `dumpsys alarm` prints a pending alarm in more than one list, so
     * the blocks are told apart by the alarm object's hash, `Alarm{<hash> …`.
     */
    private fun single(): List<String> {
        val blocks = blocks()
        val alarms = blocks.distinctBy { it.first().substringAfter("Alarm{").substringBefore(' ') }
        assertEquals("one pending alarm, not: ${blocks.map { it.first().trim() }}", 1, alarms.size)
        return alarms.single()
    }

    private fun inAnHour(): Instant = Instant.now().plusSeconds(3_600).truncatedTo(ChronoUnit.SECONDS)

    @After
    fun cancel() = alarm.cancel()

    @Test
    fun `set puts one alarm clock at the instant that wakes the device and opens the app when tapped`() {
        val at = inAnHour()

        alarm.set(at)

        val block = single()
        assertTrue(block.first(), block.first().trim().startsWith("RTC_WAKEUP"))
        assertTrue(block.first(), block.first().contains("origWhen ${at.toEpochMilli()} "))
        assertTrue(block.joinToString("\n"), block.any { it.trim() == "Alarm clock:" })
        assertTrue(block.joinToString("\n"), block.any { it.trim().startsWith("showIntent=") && it.contains("startActivity") })
    }

    @Test
    fun `setting it again replaces it`() {
        alarm.set(inAnHour())
        val later = inAnHour().plusSeconds(600)

        alarm.set(later, retry = true)

        val block = single()
        assertTrue(block.first(), block.first().contains("origWhen ${later.toEpochMilli()} "))
    }

    @Test
    fun `cancel removes it and cancelling again does nothing`() {
        alarm.set(inAnHour())

        alarm.cancel()
        alarm.cancel()

        assertEquals(emptyList<List<String>>(), blocks())
        assertNull(
            "the PendingIntent was cancelled too",
            ReminderIntents.wakeUp(
                context,
                action,
                flags =
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE,
            ),
        )
    }
}
