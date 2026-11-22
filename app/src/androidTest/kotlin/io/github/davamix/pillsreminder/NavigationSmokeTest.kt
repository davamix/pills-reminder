package io.github.davamix.pillsreminder

import android.content.pm.ActivityInfo
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The navigation skeleton of P1: every placeholder screen opens from its button, and Back returns. The test names are
 * sentences, which D8 accepts because minSdk is 33 (P1-D7). DEX names can't hold ASCII punctuation other than `$`, `-`
 * and `_`, so they have no commas.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(
        @StringRes id: Int,
    ) = compose.activity.getString(id)

    private fun tap(
        @StringRes label: Int,
    ) = compose.onNodeWithText(text(label)).performClick()

    private fun assertOn(
        @StringRes title: Int,
    ) = compose.onNodeWithText(text(title)).assertIsDisplayed()

    private fun tapBackArrow() = compose.onNodeWithContentDescription(text(R.string.navigate_back)).performClick()

    @Test
    fun `the app starts on the home screen`() {
        assertOn(R.string.home_title)
    }

    @Test
    fun `the activity asks for portrait`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
    }

    @Test
    fun `settings and the sample take open from home and Back returns`() {
        tap(R.string.home_open_settings)
        assertOn(R.string.settings_title)
        pressBack()
        assertOn(R.string.home_title)

        tap(R.string.home_sample_take)
        assertOn(R.string.take_title)
        tapBackArrow()
        assertOn(R.string.home_title)
    }

    @Test
    fun `the editors open from home and Back returns through each`() {
        tap(R.string.home_new_treatment)
        assertOn(R.string.treatment_editor_title)
        tap(R.string.treatment_editor_add_medicine)
        assertOn(R.string.medicine_editor_title)
        pressBack()
        assertOn(R.string.treatment_editor_title)
        tapBackArrow()
        assertOn(R.string.home_title)
    }

    // Reads the app's real data, as the Treatments screen does, and changes nothing. P6 T7 rewrites these tests with
    // fakes (P6-D2).
    @Test
    fun `the treatments list and its new treatment editor open from home and Back returns`() {
        tap(R.string.home_all_treatments)
        assertOn(R.string.treatments_title)
        tap(R.string.treatments_new)
        assertOn(R.string.treatment_editor_title)
        pressBack()
        assertOn(R.string.treatments_title)
        tapBackArrow()
        assertOn(R.string.home_title)
    }
}
