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
 * What needs `MainActivity`: the start screen, portrait, and P1's placeholders that remain (P7's Home and Settings).
 * P6's screens are walked with fakes in `ui.ScreensNavigationTest` (P6-D2). The test names are
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
}
