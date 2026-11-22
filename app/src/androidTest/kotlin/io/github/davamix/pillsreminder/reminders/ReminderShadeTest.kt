package io.github.davamix.pillsreminder.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TakeKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime

/**
 * The shade on the device's own notification manager (P5 SPEC §4.3.4), with a tag prefix of its own, so the app's
 * reconcile never touches these notifications (P5-D11). Only silent reminders are posted, so nothing sounds on the
 * phone; an alert is checked as built. The §4.4 example's names, with made-up IDs. Everything posted is removed after
 * each test.
 */
@RunWith(AndroidJUnit4::class)
class ReminderShadeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shade = AndroidReminderShade(context, tagPrefix = PREFIX)

    private val amoxicillin = TakeKey(MedicineId("test-amoxicillin"), LocalDateTime.of(2026, 11, 23, 16, 0))
    private val atrovent = TakeKey(MedicineId("test-atrovent"), LocalDateTime.of(2026, 11, 23, 16, 0))
    private val content =
        ReminderContent(amoxicillin, "Amoxicillin", "1 capsule", "Example", "with food", Instant.parse("2026-11-23T15:00:00Z"))

    private fun tag(key: TakeKey) = "$PREFIX/${TakeKeyCodec.encode(key)}"

    /** The notification with this tag, once the notification manager has it: up to 5 s. */
    private fun active(tag: String): StatusBarNotification? {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            manager.activeNotifications.firstOrNull { it.tag == tag }?.let { return it }
            SystemClock.sleep(50)
        }
        return null
    }

    /** Waits until no notification has this tag: up to 5 s. */
    private fun assertGone(tag: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline && manager.activeNotifications.any { it.tag == tag }) SystemClock.sleep(50)
        assertTrue("$tag is still in the shade", manager.activeNotifications.none { it.tag == tag })
    }

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        shade.prepare()
    }

    @After
    fun removeEverything() {
        manager.activeNotifications.filter { it.tag?.startsWith(PREFIX) == true }.forEach { manager.cancel(it.tag, it.id) }
    }

    @Test
    fun `a reminder is posted with its texts and time on the channel public and in the group with three actions`() {
        shade.post(content, alert = false)

        val notification = checkNotNull(active(tag(amoxicillin))) { "not posted" }.notification
        assertEquals(ReminderChannel.ID, notification.channelId)
        assertNull("no category (Q6)", notification.category)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals("io.github.davamix.pillsreminder.group.$PREFIX", notification.group)
        assertEquals(content.dueAt.toEpochMilli(), notification.`when`)
        assertEquals("Amoxicillin: 1 capsule", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Example · with food", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("Taken", "Snooze", "Skip"), notification.actions.map { it.title.toString() })
        assertTrue("not cancelled by a tap (RM-5)", notification.flags and Notification.FLAG_AUTO_CANCEL == 0)
    }

    @Test
    fun `an alert is built to sound and a silent post is muted by its group`() {
        assertEquals(Notification.GROUP_ALERT_CHILDREN, shade.build(content, alert = true).groupAlertBehavior)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, shade.build(content, alert = false).groupAlertBehavior)
    }

    @Test
    fun `without instructions the text is the treatment and without a dose the title is the medicine`() {
        val text = shade.build(content.copy(instructions = ""), alert = false).extras.getCharSequence(Notification.EXTRA_TEXT)
        val title = shade.build(content.copy(dose = ""), alert = false).extras.getCharSequence(Notification.EXTRA_TITLE)

        assertEquals("Example", text.toString())
        assertEquals("Amoxicillin", title.toString())
    }

    @Test
    fun `the actions and the tap are immutable intents of the app itself`() {
        val notification = shade.build(content, alert = false)

        assertTrue(notification.contentIntent.isActivity)
        assertTrue(notification.contentIntent.isImmutable)
        assertEquals(context.packageName, notification.contentIntent.creatorPackage)
        for (action in notification.actions) {
            assertTrue(action.actionIntent.isBroadcast)
            assertTrue(action.actionIntent.isImmutable)
            assertEquals(context.packageName, action.actionIntent.creatorPackage)
        }
    }

    @Test
    fun `shown lists the reminders with their fingerprints and ignores other notifications`() {
        val other = content.copy(key = atrovent, medicine = "Atrovent", dose = "3 puffs", instructions = "")
        shade.post(content, alert = false)
        shade.post(other, alert = false)
        val stray = Notification.Builder(context, ReminderChannel.ID).setSmallIcon(android.R.drawable.ic_dialog_info).build()
        manager.notify("$PREFIX/not-a-key", 1, stray)
        manager.notify("$PREFIX-other", 1, stray)
        checkNotNull(active("$PREFIX-other"))
        checkNotNull(active(tag(atrovent)))

        assertEquals(mapOf(amoxicillin to content.fingerprint, atrovent to other.fingerprint), shade.shown())
    }

    @Test
    fun `remove takes a reminder out of the shade`() {
        shade.post(content, alert = false)
        checkNotNull(active(tag(amoxicillin)))

        shade.remove(amoxicillin)

        assertGone(tag(amoxicillin))
        assertEquals(emptyMap<TakeKey, String>(), shade.shown())
    }

    @Test
    fun `the summary says how many takes are due and goes at zero`() {
        shade.post(content, alert = false)
        shade.summary(2)

        val summary = checkNotNull(active("$PREFIX-summary")) { "no summary" }.notification
        assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals("2 takes due", summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(Notification.GROUP_ALERT_CHILDREN, summary.groupAlertBehavior)

        shade.summary(0)
        assertGone("$PREFIX-summary")
    }

    private companion object {
        const val PREFIX = "test-take"
    }
}
