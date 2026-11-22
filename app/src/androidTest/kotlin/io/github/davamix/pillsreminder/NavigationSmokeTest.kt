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
 * What needs `MainActivity`: the start screen, portrait, and Settings from Home's settings button (P7), with the first
 * launch done for the run. P6's and P7's screens are walked with fakes in `ui` (P6-D2). The test names are
 * sentences, which D8 accepts because minSdk is 33 (P1-D7). DEX names can't hold ASCII punctuation other than `$`, `-`
 * and `_`, so they have no commas.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule(order = 0)
    val firstLaunchDone = FirstLaunchDone()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(
        @StringRes id: Int,
    ) = compose.activity.getString(id)

    private fun assertOn(
        @StringRes title: Int,
    ) = compose.onNodeWithText(text(title)).assertIsDisplayed()

    @Test
    fun `the app starts on the home screen`() {
        assertOn(R.string.home_title)
    }

    @Test
    fun `the activity asks for portrait`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
    }

    @Test
    fun `settings open from home and Back returns`() {
        compose.onNodeWithContentDescription(text(R.string.home_settings)).performClick()
        assertOn(R.string.settings_title)
        pressBack()
        assertOn(R.string.home_title)
    }
}
