package io.github.davamix.pillsreminder.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.ui.treatment.TREATMENT_LIST_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Creating the spec's §4.4 example through both editors, on Sunday 22 November at 07h30, the day before it starts (TR-1
 * to TR-4, SC-2; plan §5.2's done criterion). Fakes only (P6-D2).
 */
@RunWith(AndroidJUnit4::class)
class CreateTreatmentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = nov(22, 0).toLocalDate()
    private val dates = dateTimeText()

    private fun preview(
        count: Int,
        last: LocalDateTime,
    ) = context.resources.getQuantityString(
        R.plurals.editor_preview,
        count,
        dates.number(count.toLong()),
        dates.date(last.toLocalDate(), today),
        dates.time(last),
    )

    /** In the open medicine editor: the name, the dose, the duration in days, and the first take on Monday 23. */
    private fun medicine(
        name: String,
        dose: String,
        days: Int,
    ) {
        compose.type(R.string.editor_medicine_name, name)
        compose.type(R.string.editor_dose, dose)
        compose.type(R.string.editor_for, days.toString())
        // The suggested first take is the next full hour, today at 08:00; the date picker moves it to Monday.
        compose.tapText(dates.date(today, today))
        compose.pickDay(23)
    }

    @Test
    fun `the example is created through both editors and shows its takes and last takes`() {
        val app = FakeApp(now = nov(22, 7, 30))
        compose.showApp(app)

        compose.tap(R.string.home_all_treatments)
        compose.tap(R.string.treatments_new)
        compose.type(R.string.editor_treatment_name, "Example")
        compose.tap(R.string.editor_add_medicine)
        medicine("Amoxicillin", "1 capsule", days = 7)
        compose.onNodeWithText(preview(21, nov(30, 0))).performScrollTo().assertIsDisplayed()
        compose.tap(R.string.editor_done)
        compose.tap(R.string.editor_add_medicine)
        medicine("Atrovent", "3 puffs", days = 5)
        compose.onNodeWithText(preview(15, nov(28, 0))).performScrollTo().assertIsDisplayed()
        compose.tap(R.string.editor_done)
        compose.onNodeWithText("Amoxicillin · 1 capsule").assertHeightIsAtLeast(48.dp)
        compose.tap(R.string.editor_save)

        // The Treatment screen, in place of the editors.
        val starts = text(R.string.treatment_starts, dates.dateTime(nov(23, 8), today))
        compose.onNodeWithText(text(R.string.treatment_status_detail, text(R.string.status_upcoming), starts)).assertIsDisplayed()
        for ((count, last) in listOf("21" to nov(30, 0), "15" to nov(28, 0))) {
            val list = compose.onNodeWithTag(TREATMENT_LIST_TAG)
            list.performScrollToNode(hasText(text(R.string.treatment_progress, "0", count)))
            list.performScrollToNode(hasText(text(R.string.treatment_medicine_last_take, dates.dateTime(last, today))))
        }
        val stored = app.data.treatments.single()
        assertEquals(listOf("Amoxicillin", "Atrovent"), stored.medicines.map { it.name })
        assertEquals(listOf(nov(23, 8), nov(23, 8)), stored.medicines.map { it.schedule.firstTake })

        pressBack()
        compose.onNodeWithText(text(R.string.status_upcoming)).assertIsDisplayed()
        compose.onNodeWithText("Example").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
}
