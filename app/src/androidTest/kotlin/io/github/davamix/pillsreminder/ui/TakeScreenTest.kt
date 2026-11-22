package io.github.davamix.pillsreminder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.data.DeviceExample
import io.github.davamix.pillsreminder.navigation.TakeRoute
import io.github.davamix.pillsreminder.ui.treatment.TREATMENT_LIST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A take's answers on the Take screen: Taken, Change time, Skip and Undo (TK-2, TK-5). Fakes only (P6-D2). */
@RunWith(AndroidJUnit4::class)
class TakeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val amox0800 = TakeKey(DeviceExample.amoxicillinId, nov(23, 8))
    private val amox1600 = TakeKey(DeviceExample.amoxicillinId, nov(23, 16))
    private val today = nov(23, 0).toLocalDate()
    private val dates = dateTimeText()

    private fun data() = PillsData(listOf(DeviceExample.treatment), mapOf(amox0800 to Answer.Taken(nov(23, 8, 5))))

    private fun assertState(id: Int) = compose.onNodeWithText(text(id)).assertIsDisplayed()

    @Test
    fun `a Due take opened from its treatment is answered then changed then set back`() {
        val app = FakeApp(data(), now = nov(23, 16, 5))
        compose.showApp(app)
        compose.tap(R.string.home_all_treatments)
        compose.tapText("Example")
        val next = text(R.string.treatment_next, dates.dateTime(nov(23, 16), today), text(R.string.take_state_due))
        compose.onNodeWithTag(TREATMENT_LIST_TAG).performScrollToNode(hasText(next))
        // Both medicines are due at 16:00: Amoxicillin's card comes first.
        compose
            .onAllNodesWithText(next)
            .onFirst()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertState(R.string.take_state_due)
        compose.onNodeWithText(text(R.string.take_change_time)).assertDoesNotExist()
        compose.tap(R.string.take_taken)
        compose.onNodeWithText(text(R.string.taken_at, dates.time(nov(23, 16, 5)))).assertIsDisplayed()
        assertEquals(Answer.Taken(nov(23, 16, 5)), app.data.answers[amox1600])

        // Change time: the date picker moves it to the day before.
        compose.tap(R.string.take_change_time)
        compose.tapText(dates.date(today, today))
        compose.pickDay(22)
        compose.tap(R.string.take_change_time_save)
        compose.onNodeWithText(text(R.string.taken_on, dates.dateTime(nov(22, 16, 5), today))).assertIsDisplayed()
        assertEquals(Answer.Taken(nov(22, 16, 5)), app.data.answers[amox1600])

        compose.tap(R.string.take_skip)
        assertState(R.string.take_state_skipped)
        assertEquals(Answer.Skipped, app.data.answers[amox1600])

        compose.tap(R.string.take_undo)
        assertState(R.string.take_state_due)
        assertNull(app.data.answers[amox1600])
    }

    @Test
    fun `an older answered take says it cannot be changed and offers nothing`() {
        var take by mutableStateOf<TakeRoute?>(null)
        val answers = data().answers + (amox1600 to Answer.Taken(nov(23, 16, 2)))
        compose.showApp(FakeApp(data().copy(answers = answers), now = nov(23, 16, 5)), openTake = { take }, onTakeOpened = { take = null })

        take = TakeRoute(DeviceExample.amoxicillinId.value, nov(23, 8).toString())

        compose.onNodeWithText(text(R.string.take_only_last)).assertIsDisplayed()
        for (id in listOf(R.string.take_taken, R.string.take_skip, R.string.take_change_time, R.string.take_undo)) {
            // As buttons: "Taken" is also the take's state.
            compose.onNode(hasText(text(id)) and hasClickAction()).assertDoesNotExist()
        }
    }
}
