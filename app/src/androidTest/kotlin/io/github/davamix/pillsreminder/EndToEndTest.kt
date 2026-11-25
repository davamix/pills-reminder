package io.github.davamix.pillsreminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.core.schedule.Take
import io.github.davamix.pillsreminder.core.schedule.TakeState
import io.github.davamix.pillsreminder.core.schedule.takesOf
import io.github.davamix.pillsreminder.core.time.suggestedFirstTake
import io.github.davamix.pillsreminder.reminders.AndroidReminderShade
import io.github.davamix.pillsreminder.reminders.TakeKeyCodec
import io.github.davamix.pillsreminder.ui.context
import io.github.davamix.pillsreminder.ui.dateTimeText
import io.github.davamix.pillsreminder.ui.home.HOME_LIST_TAG
import io.github.davamix.pillsreminder.ui.tap
import io.github.davamix.pillsreminder.ui.tapText
import io.github.davamix.pillsreminder.ui.text
import io.github.davamix.pillsreminder.ui.type
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The real path, from the editor to the shade and back (P8 SPEC §4.3.2): through `MainActivity` and the app's own
 * container, with its store, its reminders and its receivers. Every other UI test uses fakes (P6-D2).
 *
 * It adds the spec's §4.4 example to the debug app's real data, and deletes it afterwards whatever happens. Its first
 * takes are yesterday, so each medicine's latest take is Due at once (TR-4), and it alerts once with sound, on the phone
 * too (P8 Q1). The notification's own `PendingIntent`s stand in for taps on the shade, which A4 and A6 check by hand.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    @get:Rule(order = 0)
    val firstLaunchDone = FirstLaunchDone()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val container = (context as PillsReminderApp).container
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val dates = dateTimeText()

    /** The treatments there before the test, which it leaves alone. */
    private var before: Set<TreatmentId>? = null

    @Before
    fun noteTheData() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        before = stored().treatments.map { it.id }.toSet()
    }

    @After
    fun deleteWhatItCreated() {
        if (before == null) return
        val created = created()
        val medicines = created.flatMap { treatment -> treatment.medicines.map { it.id } }.toSet()
        runBlocking { created.forEach { container.repository.deleteTreatment(it.id) } }
        // The reconcile that follows the deletion removes their reminders (TR-9).
        compose.waitUntil("its reminders leave the shade", REMINDER_TIMEOUT) { shown().none { it.medicineId in medicines } }
    }

    @Test
    fun `a treatment created in the app gets reminders that are answered from the notification and the Take screen`() {
        awayFromTheHour()
        val started = LocalDateTime.now(container.clock).truncatedTo(ChronoUnit.MINUTES)

        compose.tap(R.string.home_new_treatment)
        compose.type(R.string.editor_treatment_name, "Example")
        // The editor suggests every 8 hours for 7 days.
        medicine("Amoxicillin", "1 capsule", instructions = "with food")
        medicine("Atrovent", "3 puffs", days = 5)
        compose.tap(R.string.editor_save)
        compose.waitUntil("the treatment is stored", STORE_TIMEOUT) { created().isNotEmpty() }
        val treatment = created().single()
        assertEquals(listOf("Amoxicillin", "Atrovent"), treatment.medicines.map { it.name })
        val amoxicillin = dueTake(treatment.medicines[0].id)
        val atrovent = dueTake(treatment.medicines[1].id)

        // One reminder per take, each alerting, with its content and its three actions (RM-1, RM-3, RM-4).
        compose.waitUntil("both reminders are in the shade", REMINDER_TIMEOUT) {
            reminder(amoxicillin.key) != null && reminder(atrovent.key) != null
        }
        val withFood = text(R.string.reminder_text, "Example", "with food")
        assertReminder(reminder(amoxicillin.key), text(R.string.reminder_title, "Amoxicillin", "1 capsule"), withFood)
        assertReminder(reminder(atrovent.key), text(R.string.reminder_title, "Atrovent", "3 puffs"), "Example")

        // Home, from the Treatment screen that replaced the editors: both Due, each with Taken (HM-2, HM-3).
        pressBack()
        assertRow("Amoxicillin · 1 capsule", progress(0, 21), due = true)
        assertRow("Atrovent · 3 puffs", progress(0, 15), due = true)

        // Taken from the notification: the real receiver records it, and the reminder goes (RM-3, TK-2).
        checkNotNull(reminder(amoxicillin.key)).action(R.string.reminder_action_taken).actionIntent.send()
        compose.waitUntil("Amoxicillin's reminder leaves the shade", REMINDER_TIMEOUT) { reminder(amoxicillin.key) == null }
        val answer = stored().answers[amoxicillin.key]
        assertTrue("Amoxicillin's take was answered $answer", answer is Answer.Taken)
        val at = (answer as Answer.Taken).at
        assertTrue("Taken at $at, during the test from $started", at >= started && at <= LocalDateTime.now(container.clock))
        // Home shows the answer given from the notification: no longer Due (HM-3).
        assertRow("Amoxicillin · 1 capsule", progress(1, 21), due = false)
        assertRow("Atrovent · 3 puffs", progress(0, 15), due = true)

        // A tap on the reminder opens its take (RM-6); Taken there removes the reminder (RM-7).
        checkNotNull(reminder(atrovent.key)).contentIntent.send()
        val planned = text(R.string.take_planned, dates.dateTime(atrovent.key.plannedAt, LocalDateTime.now(container.clock).toLocalDate()))
        val takeScreen = hasText(planned)
        compose.waitUntil("the Take screen opens", SCREEN_TIMEOUT) { compose.onAllNodes(takeScreen).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.take_title)).assertIsDisplayed()
        compose.onNodeWithText("Atrovent · 3 puffs").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.take_state_due)).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.take_taken)) and hasClickAction()).performClick()
        compose.waitUntil("Atrovent's reminder leaves the shade", REMINDER_TIMEOUT) { reminder(atrovent.key) == null }
        assertTrue(stored().answers[atrovent.key] is Answer.Taken)

        pressBack()
        assertRow("Atrovent · 3 puffs", progress(1, 15), due = false)
        assertRow("Amoxicillin · 1 capsule", progress(1, 21), due = false)
    }

    /** Waits for the next full hour when it is less than 3 minutes away, so the Due takes can't change during the test. */
    private fun awayFromTheHour() {
        val now = ZonedDateTime.now(container.clock)
        val left = Duration.between(now, now.truncatedTo(ChronoUnit.HOURS).plusHours(1))
        if (left < Duration.ofMinutes(3)) Thread.sleep(left.toMillis() + 5_000)
    }

    /** Adds a medicine through the medicine editor, with its first take yesterday at the suggested time. */
    private fun medicine(
        name: String,
        dose: String,
        instructions: String = "",
        days: Int? = null,
    ) {
        compose.tap(R.string.editor_add_medicine)
        compose.type(R.string.editor_medicine_name, name)
        compose.type(R.string.editor_dose, dose)
        if (instructions.isNotEmpty()) compose.type(R.string.editor_instructions, instructions)
        if (days != null) compose.type(R.string.editor_for, days.toString())
        firstTakeYesterday()
        compose.tap(R.string.editor_done)
    }

    /**
     * Moves the first take to yesterday in the date picker, keeping the suggested time (the next full hour). Every 8
     * hours, its latest take before now is then Due, and Missed only at the next full hour.
     */
    private fun firstTakeYesterday() {
        val now = ZonedDateTime.now(container.clock)
        val today = now.toLocalDate()
        val suggested = suggestedFirstTake(now).toLocalDate()
        val yesterday = today.minusDays(1)
        compose.tapText(dates.date(suggested, today))
        // The picker opens on the suggested date's month.
        if (YearMonth.from(yesterday) != YearMonth.from(suggested)) {
            compose.onNodeWithContentDescription(PREVIOUS_MONTH).performClick()
        }
        compose.onNode(dayCell(yesterday.dayOfMonth)).performClick()
        compose.tap(R.string.action_ok)
    }

    /**
     * The picker's cell for [day] of the month shown. A cell's text is its whole date, such as "Sunday, November 22,
     * 2026", so the day is matched as a whole number: "2" mustn't match the year.
     */
    private fun dayCell(day: Int): SemanticsMatcher {
        val number = Regex("\\b$day\\b")
        val matcher =
            SemanticsMatcher("the day cell for $day") { node ->
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .any { number.containsMatchIn(it.text) }
            }
        return matcher and hasClickAction()
    }

    private fun stored(): PillsData {
        val data = runBlocking { container.repository.data.first() }
        return checkNotNull((data as? StoredData.Readable)?.data) { "The debug app's stored data can't be read" }
    }

    /** The treatments the test created: those that weren't there before it. */
    private fun created(): List<Treatment> = stored().treatments.filter { it.id !in checkNotNull(before) }

    /** The medicine's Due take, now. */
    private fun dueTake(medicine: MedicineId): Take {
        val take = checkNotNull(stored().takesOf(medicine)?.nextTake(ZonedDateTime.now(container.clock))) { "No next take" }
        assertEquals("The latest take of a first take yesterday", TakeState.DUE, take.state)
        return take
    }

    /** The take keys of the app's reminders in the shade. The test runs in the app's process, so it sees them. */
    private fun shown(): List<TakeKey> =
        manager.activeNotifications.mapNotNull { status ->
            status.tag
                ?.removePrefix("${AndroidReminderShade.TAG_PREFIX}/")
                ?.takeIf { it != status.tag }
                ?.let(TakeKeyCodec::decode)
        }

    /** The app's reminder for [key] in the shade, or `null`. */
    private fun reminder(key: TakeKey): Notification? =
        manager.activeNotifications
            .firstOrNull { it.tag == "${AndroidReminderShade.TAG_PREFIX}/${TakeKeyCodec.encode(key)}" }
            ?.notification

    private fun assertReminder(
        notification: Notification?,
        title: String,
        body: String,
    ) {
        val posted = checkNotNull(notification) { "No reminder for $title" }
        assertEquals(title, posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(body, posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val actions = listOf(R.string.reminder_action_taken, R.string.reminder_action_snooze, R.string.reminder_action_skip)
        assertEquals(actions.map { text(it) }, posted.actions.map { it.title.toString() })
        // An alerting post: a silent one only lets its summary alert (P5 Q8).
        assertEquals("$title alerts", Notification.GROUP_ALERT_CHILDREN, posted.groupAlertBehavior)
    }

    private fun Notification.action(label: Int): Notification.Action = actions.single { it.title.toString() == text(label) }

    private fun progress(
        answered: Int,
        total: Int,
    ) = text(R.string.home_row_progress, "Example", answered.toString(), total.toString())

    /**
     * Home's row for the medicine with that progress: a Due row has Taken (HM-2), a Next row doesn't. It waits for the
     * row, which follows the store, and scrolls the list to it.
     */
    private fun assertRow(
        medicine: String,
        progress: String,
        due: Boolean,
    ) {
        val row = hasText(medicine) and hasText(progress) and hasClickAction()
        val list = compose.onNodeWithTag(HOME_LIST_TAG)
        compose.waitUntil("Home shows $medicine with $progress", SCREEN_TIMEOUT) {
            try {
                list.performScrollToNode(row)
                true
            } catch (e: AssertionError) {
                false
            }
        }
        val taken = compose.onAllNodes(hasText(text(R.string.home_taken)) and hasClickAction() and hasAnyAncestor(row))
        assertEquals("$medicine, $progress: Taken", if (due) 1 else 0, taken.fetchSemanticsNodes().size)
    }

    private companion object {
        const val STORE_TIMEOUT = 5_000L
        const val REMINDER_TIMEOUT = 10_000L
        const val SCREEN_TIMEOUT = 10_000L

        /** Material 3's date picker, in English (`localeFilters`, P6 Q8). */
        const val PREVIOUS_MONTH = "Change to previous month"
    }
}
