package io.github.davamix.pillsreminder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.data.DeviceExample
import io.github.davamix.pillsreminder.ui.treatment.TREATMENT_LIST_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Editing a started treatment (TR-10): on Tuesday 24 November at 10h00, with Amoxicillin's first four takes taken, its
 * interval goes from 8 to 12 hours, as in P2 SPEC §4.3.6's example. Fakes only (P6-D2).
 */
@RunWith(AndroidJUnit4::class)
class EditStartedTreatmentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a started medicine edited from 8 to 12 hours keeps its four answers and has 15 takes`() {
        val times = listOf(nov(23, 8), nov(23, 16), nov(24, 0), nov(24, 8))
        val answers = times.associate { TakeKey(DeviceExample.amoxicillinId, it) to Answer.Taken(it) }
        val app = FakeApp(PillsData(listOf(DeviceExample.treatment), answers), now = nov(24, 10))
        val dates = dateTimeText()
        compose.showApp(app)

        compose.tap(R.string.home_all_treatments)
        compose.tapText("Example")
        compose.onNodeWithContentDescription(text(R.string.treatment_edit)).performClick()
        compose.tapText("Amoxicillin · 1 capsule")
        compose.type(R.string.editor_every, "12")

        compose.onNodeWithText(text(R.string.editor_note_started)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.editor_note_past)).assertDoesNotExist()
        val last = nov(29, 20)
        val preview =
            context.resources.getQuantityString(
                R.plurals.editor_preview,
                15,
                "15",
                dates.date(last.toLocalDate(), nov(24, 0).toLocalDate()),
                dates.time(last),
            )
        compose.onNodeWithText(preview).performScrollTo().assertIsDisplayed()
        compose.tap(R.string.editor_done)
        compose.tap(R.string.editor_save)

        compose.onNodeWithTag(TREATMENT_LIST_TAG).performScrollToNode(hasText(text(R.string.treatment_progress, "4", "15")))
        compose.onNodeWithText(text(R.string.treatment_progress, "4", "15")).assertIsDisplayed()
    }
}
