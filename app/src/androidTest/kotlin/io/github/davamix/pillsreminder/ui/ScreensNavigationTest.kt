package io.github.davamix.pillsreminder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.data.DeviceExample
import io.github.davamix.pillsreminder.navigation.TakeRoute
import io.github.davamix.pillsreminder.ui.treatment.TREATMENT_LIST_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P6's screens open from each other and Back returns (P6 SPEC §4.3.1), and a tapped reminder's take opens above an open
 * editor, which keeps its changes (P6 Q3, §4.3.12). Fakes only (P6-D2); `NavigationSmokeTest` keeps the checks that
 * need `MainActivity`.
 */
@RunWith(AndroidJUnit4::class)
class ScreensNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = nov(23, 10, 30)
    private val data =
        PillsData(
            listOf(DeviceExample.treatment),
            mapOf(
                TakeKey(DeviceExample.amoxicillinId, nov(23, 8)) to Answer.Taken(nov(23, 8, 5)),
            ),
        )

    private fun assertOn(id: Int) = compose.onNodeWithText(text(id)).assertIsDisplayed()

    @Test
    fun `a treatment its editor and its takes open from the treatments list and Back returns through each`() {
        compose.showApp(FakeApp(data, now))
        val dates = dateTimeText()
        val today = now.toLocalDate()

        compose.tap(R.string.home_all_treatments)
        assertOn(R.string.treatments_title)
        compose.tapText("Example")
        assertOn(R.string.treatment_title)
        compose.onNodeWithContentDescription(text(R.string.treatment_edit)).performClick()
        assertOn(R.string.editor_edit_treatment)
        compose.tapText("Atrovent · 3 puffs")
        assertOn(R.string.editor_edit_medicine)
        pressBack()
        assertOn(R.string.editor_edit_treatment)
        pressBack()
        assertOn(R.string.treatment_title)

        val last =
            text(R.string.treatment_last_answered, dates.dateTime(nov(23, 8), today), text(R.string.taken_at, dates.time(nov(23, 8, 5))))
        compose.onNodeWithTag(TREATMENT_LIST_TAG).performScrollToNode(hasText(last))
        compose.tapText(last)
        assertOn(R.string.take_title)
        compose.onNodeWithText(text(R.string.take_change_time)).assertIsDisplayed()
        compose.tapBack()
        assertOn(R.string.treatment_title)
        compose.tapBack()
        assertOn(R.string.treatments_title)
        compose.tap(R.string.treatments_new)
        assertOn(R.string.editor_new_treatment)
        compose.tapBack()
        assertOn(R.string.treatments_title)
        compose.tapBack()
        assertOn(R.string.home_title)
    }

    @Test
    fun `the take of a tapped reminder opens above an open editor and Back returns to it with its changes`() {
        var take by mutableStateOf<TakeRoute?>(null)
        compose.showApp(FakeApp(data, now), openTake = { take }, onTakeOpened = { take = null })
        compose.tap(R.string.home_new_treatment)
        compose.type(R.string.editor_treatment_name, "Example 2")
        closeSoftKeyboard()

        take = TakeRoute(DeviceExample.amoxicillinId.value, nov(23, 16).toString())
        assertOn(R.string.take_title)
        // A second reminder replaces the first take rather than stacking above it.
        take = TakeRoute(DeviceExample.atroventId.value, nov(23, 16).toString())
        compose.onNodeWithText("Atrovent · 3 puffs").assertIsDisplayed()

        compose.tapBack()
        assertOn(R.string.editor_new_treatment)
        compose.onNode(hasSetTextAction() and hasText("Example 2")).assertIsDisplayed()
    }

    @Test
    fun `the take of a tapped reminder opens right above Home when no editor is open`() {
        var take by mutableStateOf<TakeRoute?>(null)
        compose.showApp(FakeApp(data, now), openTake = { take }, onTakeOpened = { take = null })
        compose.tap(R.string.home_all_treatments)
        compose.tapText("Example")

        take = TakeRoute(DeviceExample.amoxicillinId.value, nov(23, 16).toString())
        assertOn(R.string.take_title)

        compose.tapBack()
        assertOn(R.string.home_title)
    }
}
