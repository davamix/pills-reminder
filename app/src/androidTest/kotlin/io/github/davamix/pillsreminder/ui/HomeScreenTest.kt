package io.github.davamix.pillsreminder.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.IntervalUnit
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineDuration
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.Schedule
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.Treatment
import io.github.davamix.pillsreminder.core.model.TreatmentId
import io.github.davamix.pillsreminder.data.DeviceExample
import io.github.davamix.pillsreminder.ui.home.HOME_LIST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home's sections, Taken and Taken early, its empty and unreadable states, and where it leads (HM-1–HM-4, TK-4). Fakes only. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val dates = dateTimeText()
    private val today = nov(23, 0).toLocalDate()

    private val amox0800 = TakeKey(DeviceExample.amoxicillinId, nov(23, 8))
    private val amox1600 = TakeKey(DeviceExample.amoxicillinId, nov(23, 16))
    private val atro0800 = TakeKey(DeviceExample.atroventId, nov(23, 8))
    private val atro1600 = TakeKey(DeviceExample.atroventId, nov(23, 16))

    private val amoxicillin = "Amoxicillin · 1 capsule"
    private val atrovent = "Atrovent · 3 puffs"

    /** A treatment that starts on Wednesday 25 November at 08:00: Upcoming on Monday. */
    private val later =
        Treatment(
            TreatmentId("later"),
            "Later",
            "",
            listOf(
                Medicine(
                    MedicineId("later-atrovent"),
                    "Atrovent",
                    "2 puffs",
                    "",
                    Schedule(nov(25, 8), Interval(8, IntervalUnit.HOURS), MedicineDuration.Days(2)),
                ),
            ),
        )

    /** The §4.4 example with both 08:00 takes Taken, and [more] answers. */
    private fun morningTaken(vararg more: Pair<TakeKey, Answer>) =
        PillsData(
            listOf(DeviceExample.treatment, later),
            mapOf(amox0800 to Answer.Taken(nov(23, 8, 5)), atro0800 to Answer.Taken(nov(23, 8, 5))) + more,
        )

    private fun top(text: String) =
        compose
            .onNodeWithText(text)
            .fetchSemanticsNode()
            .boundsInRoot.top

    private fun assertShown(text: String) = compose.onNodeWithText(text).assertIsDisplayed()

    private fun takenButtons() = compose.onAllNodes(hasText(text(R.string.home_taken)) and hasClickAction())

    private fun takenEarlyButtons() = compose.onAllNodes(hasText(text(R.string.home_taken_early)) and hasClickAction())

    @Test
    fun `Due then Next then Upcoming each with its rows in the spec example at 16h05`() {
        compose.showApp(FakeApp(morningTaken(atro1600 to Answer.Taken(nov(23, 16, 1))), now = nov(23, 16, 5)))
        compose.onNodeWithTag(HOME_LIST_TAG).performScrollToNode(hasText("Later"))

        val order = listOf(text(R.string.home_due), amoxicillin, text(R.string.home_next), atrovent, text(R.string.home_upcoming), "Later")
        val tops = order.map { top(it) }
        assertEquals(tops.sorted(), tops)

        assertShown(
            text(R.string.home_row_when, dates.dateTime(nov(23, 16), today), text(R.string.home_time_ago, text(R.string.home_minutes, 5))),
        )
        assertShown(text(R.string.home_row_progress, "Example", "1", "21"))
        val in755 = text(R.string.home_time_in, text(R.string.home_hours_minutes, 7, 55))
        assertShown(text(R.string.home_row_when, dates.dateTime(nov(24, 0), today), in755))
        assertShown(text(R.string.home_row_progress, "Example", "2", "15"))
        val in1day15h = text(R.string.home_time_in, text(R.string.home_days_hours, quantity(R.plurals.home_days, 1), 15))
        assertShown(text(R.string.home_upcoming_starts, dates.dateTime(nov(25, 8), today), in1day15h))

        // The Due row has Taken; the Next row Taken early (TK-4). Rows and buttons are at least 48 dp tall (NF-7).
        takenButtons().assertCountEquals(1)
        takenEarlyButtons().assertCountEquals(1)
        takenButtons().onFirst().assertTouchHeightIsAtLeast(48.dp)
        takenEarlyButtons().onFirst().assertTouchHeightIsAtLeast(48.dp)
        for (row in listOf(amoxicillin, atrovent, "Later")) compose.onNode(hasText(row) and hasClickAction()).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `a due take marked Taken moves to Next`() {
        val app = FakeApp(morningTaken(), now = nov(23, 16, 5))
        compose.showApp(app)
        takenButtons().assertCountEquals(2)

        // Amoxicillin's row comes first: ties are by the medicine's place.
        takenButtons().onFirst().performClick()
        compose.waitUntil { app.data.answers[amox1600] != null }

        assertEquals(Answer.Taken(nov(23, 16, 5)), app.data.answers[amox1600])
        compose.waitForIdle()
        val order = listOf(text(R.string.home_due), atrovent, text(R.string.home_next), amoxicillin)
        val tops = order.map { top(it) }
        assertEquals(tops.sorted(), tops)
        takenButtons().assertCountEquals(1)
        takenEarlyButtons().assertCountEquals(1)
    }

    @Test
    fun `Taken early asks first and Cancel changes nothing then confirming marks the take`() {
        val app = FakeApp(morningTaken(), now = nov(23, 10, 30))
        compose.showApp(app)
        takenEarlyButtons().assertCountEquals(2)

        takenEarlyButtons().onFirst().performClick()
        assertShown(text(R.string.home_taken_early_title))
        assertShown(text(R.string.home_taken_early_confirm, amoxicillin, dates.dateTime(nov(23, 16), today)))
        compose.tap(R.string.action_cancel)
        compose.onNodeWithText(text(R.string.home_taken_early_title)).assertDoesNotExist()
        assertNull(app.data.answers[amox1600])

        takenEarlyButtons().onFirst().performClick()
        compose.onNode(hasText(text(R.string.home_taken_early)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil { app.data.answers[amox1600] != null }

        assertEquals(Answer.Taken(nov(23, 10, 30)), app.data.answers[amox1600])
        compose.waitForIdle()
        // Amoxicillin now shows its 00:00 take, which can't be marked early too: only Atrovent's button is left.
        val in1330 = text(R.string.home_time_in, text(R.string.home_hours_minutes, 13, 30))
        assertShown(text(R.string.home_row_when, dates.dateTime(nov(24, 0), today), in1330))
        takenEarlyButtons().assertCountEquals(1)
    }

    @Test
    fun `with nothing active or upcoming Home says so and New treatment opens the editor`() {
        compose.showApp(FakeApp(now = nov(23, 10, 30)))

        assertShown(text(R.string.home_empty_title))
        assertShown(text(R.string.home_empty_text))
        compose.tap(R.string.home_new_treatment)
        assertShown(text(R.string.editor_new_treatment))
    }

    @Test
    fun `unreadable data shows its message with no New treatment and All treatments and Settings stay`() {
        compose.showApp(FakeApp(now = nov(23, 10, 30), unreadable = true))

        assertShown(text(R.string.problem_unreadable))
        compose.onNodeWithText(text(R.string.home_new_treatment)).assertDoesNotExist()
        assertShown(text(R.string.home_all_treatments))
        compose.onNodeWithContentDescription(text(R.string.home_settings)).assertIsDisplayed()
    }

    @Test
    fun `a row opens its treatment and an Upcoming one too`() {
        compose.showApp(FakeApp(morningTaken(), now = nov(23, 16, 5)))

        compose.tapText(atrovent)
        assertShown(text(R.string.treatment_title))
        assertShown("Example")
        pressBack()

        compose.onNodeWithTag(HOME_LIST_TAG).performScrollToNode(hasText("Later"))
        compose.tapText("Later")
        assertShown(text(R.string.treatment_title))
        assertShown("Later")
    }

    @Test
    fun `the warning says how many settings to check and its Check opens Settings`() {
        val app = FakeApp(morningTaken(), now = nov(23, 16, 5))
        app.checkDevice.checks = allSet.copy(batteryOptimised = true)
        compose.showApp(app)

        assertShown(quantity(R.plurals.home_check_warning, 1))
        compose.onNode(hasText(text(R.string.home_check_open)) and hasClickAction()).assertTouchHeightIsAtLeast(48.dp).performClick()
        assertShown(text(R.string.settings_title))
        assertShown(text(R.string.settings_battery_missing))

        pressBack()
        assertShown(text(R.string.home_title))
        assertTrue(app.checkDevice.opened.isEmpty())
    }
}
