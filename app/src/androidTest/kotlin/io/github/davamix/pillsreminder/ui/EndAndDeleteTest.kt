package io.github.davamix.pillsreminder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.data.DeviceExample
import io.github.davamix.pillsreminder.ui.treatment.TREATMENT_LIST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ending a treatment early and deleting it, each after its confirmation (TR-9, TR-11, P6 Q6). Fakes only (P6-D2). */
@RunWith(AndroidJUnit4::class)
class EndAndDeleteTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scrollTo(value: String) = compose.onNodeWithTag(TREATMENT_LIST_TAG).performScrollToNode(hasText(value))

    @Test
    fun `End early asks first and then ends the treatment and Delete asks first and then deletes it`() {
        val app = FakeApp(DeviceExample.example, now = nov(23, 10, 30))
        val dates = dateTimeText()
        compose.showApp(app)
        compose.tap(R.string.home_all_treatments)
        compose.tapText("Example")

        scrollTo(text(R.string.treatment_end))
        compose.tap(R.string.treatment_end)
        compose.tap(R.string.action_cancel)
        compose.onNodeWithText(text(R.string.status_active)).assertIsDisplayed()

        scrollTo(text(R.string.treatment_end))
        compose.tap(R.string.treatment_end)
        compose.tap(R.string.treatment_end_action)
        val ended = text(R.string.treatment_ended, dates.dateTime(nov(23, 10, 30), nov(23, 0).toLocalDate()))
        compose.onNodeWithText(text(R.string.treatment_status_detail, text(R.string.status_finished), ended)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.treatment_end)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.treatment_edit)).assertDoesNotExist()
        val stored = app.data.treatments.single()
        assertEquals(nov(23, 10, 30), stored.endedAt)

        scrollTo(text(R.string.treatment_delete))
        compose.tap(R.string.treatment_delete)
        compose.tap(R.string.action_cancel)
        assertEquals(1, app.data.treatments.size)
        scrollTo(text(R.string.treatment_delete))
        compose.tap(R.string.treatment_delete)
        compose.tap(R.string.treatment_delete_action)

        compose.onNodeWithText(text(R.string.treatments_title)).assertIsDisplayed()
        compose.onNodeWithText("Example").assertDoesNotExist()
        assertTrue(app.data.treatments.isEmpty())
        assertTrue(app.data.answers.isEmpty())
    }
}
