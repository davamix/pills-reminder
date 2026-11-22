package io.github.davamix.pillsreminder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davamix.pillsreminder.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Validation messages on Save and Done, and each one going once fixed (TR-8, P6 SPEC §4.3.8). Fakes only (P6-D2). */
@RunWith(AndroidJUnit4::class)
class EditorValidationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun assertShown(id: Int) = compose.onNodeWithText(text(id)).performScrollTo().assertIsDisplayed()

    private fun assertGone(id: Int) = compose.onNodeWithText(text(id)).assertDoesNotExist()

    @Test
    fun `Save shows the treatment errors and Done the medicine errors and each goes once fixed`() {
        compose.showApp(FakeApp(now = nov(22, 10)))
        compose.tap(R.string.home_new_treatment)
        assertGone(R.string.editor_error_treatment_name)

        compose.tap(R.string.editor_save)
        assertShown(R.string.editor_error_treatment_name)
        assertShown(R.string.editor_error_no_medicine)

        compose.type(R.string.editor_treatment_name, "Example")
        assertGone(R.string.editor_error_treatment_name)

        compose.tap(R.string.editor_add_medicine)
        compose.type(R.string.editor_every, "")
        compose.type(R.string.editor_for, "")
        assertGone(R.string.editor_error_medicine_name)
        assertShown(R.string.editor_preview_missing)
        compose.tap(R.string.editor_done)
        assertShown(R.string.editor_error_medicine_name)
        assertShown(R.string.editor_error_amount)
        assertShown(R.string.editor_error_duration)

        compose.type(R.string.editor_medicine_name, "Amoxicillin")
        compose.type(R.string.editor_every, "8")
        compose.tap(R.string.editor_no_end)
        assertGone(R.string.editor_error_medicine_name)
        assertGone(R.string.editor_error_amount)
        assertGone(R.string.editor_error_duration)
        assertShown(R.string.editor_preview_no_end)
        compose.tap(R.string.editor_done)

        compose.onNodeWithText("Amoxicillin").assertIsDisplayed()
        assertGone(R.string.editor_error_no_medicine)
    }
}
