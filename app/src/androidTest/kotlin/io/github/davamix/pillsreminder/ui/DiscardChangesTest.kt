package io.github.davamix.pillsreminder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Leaving an editor with unsaved changes asks first; without changes it doesn't (TR-13). Fakes only (P6-D2). */
@RunWith(AndroidJUnit4::class)
class DiscardChangesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun assertOn(id: Int) = compose.onNodeWithText(text(id)).assertIsDisplayed()

    private fun assertNoDialog() = compose.onNodeWithText(text(R.string.editor_discard)).assertDoesNotExist()

    @Test
    fun `the treatment editor leaves at once without changes and asks first with them`() {
        compose.showApp(FakeApp(now = nov(22, 10)))
        compose.tap(R.string.home_new_treatment)
        compose.tapBack()
        assertNoDialog()
        assertOn(R.string.home_title)

        compose.tap(R.string.home_new_treatment)
        compose.type(R.string.editor_treatment_name, "Example")
        closeSoftKeyboard()
        pressBack()
        assertOn(R.string.editor_discard)
        compose.tap(R.string.editor_keep_editing)
        compose.onNode(hasSetTextAction() and hasText("Example")).assertIsDisplayed()

        compose.tapBack()
        assertOn(R.string.editor_discard)
        compose.tap(R.string.editor_discard_action)
        assertOn(R.string.home_title)
    }

    @Test
    fun `the medicine editor leaves at once without changes and asks first with them`() {
        compose.showApp(FakeApp(now = nov(22, 10)))
        compose.tap(R.string.home_new_treatment)
        compose.tap(R.string.editor_add_medicine)
        closeSoftKeyboard()
        pressBack()
        assertNoDialog()
        assertOn(R.string.editor_new_treatment)

        compose.tap(R.string.editor_add_medicine)
        compose.type(R.string.editor_medicine_name, "Amoxicillin")
        compose.tapBack()
        assertOn(R.string.editor_discard)
        compose.tap(R.string.editor_discard_action)

        assertOn(R.string.editor_new_treatment)
        compose.onNodeWithText("Amoxicillin", substring = true).assertDoesNotExist()
        // The treatment draft has no changes left, so it leaves at once.
        compose.tapBack()
        assertNoDialog()
        assertOn(R.string.home_title)
    }
}
