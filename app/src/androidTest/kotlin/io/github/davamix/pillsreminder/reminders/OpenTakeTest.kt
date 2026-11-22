package io.github.davamix.pillsreminder.reminders

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.davamix.pillsreminder.MainActivity
import io.github.davamix.pillsreminder.R
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.navigation.AppNavHost
import io.github.davamix.pillsreminder.navigation.HomeRoute
import io.github.davamix.pillsreminder.navigation.TakeRoute
import io.github.davamix.pillsreminder.takeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * A tapped reminder opens its take (RM-6, P5 SPEC §4.3.8). The Take screen is P1's placeholder until P6. Each test goes
 * through `MainActivity`, which reconciles the app's real data when it starts, as opening the app does; none changes it.
 */
@RunWith(AndroidJUnit4::class)
class OpenTakeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = TakeKey(MedicineId("open-take-test"), LocalDateTime.of(2026, 11, 23, 16, 0))

    /**
     * The reminder's content intent, as the shade sets it, without the flags that pick its task: the scenario starts the
     * activity in a task of its own, and these would bring forward the app's own task, if the device has one.
     */
    private fun reminderIntent() = ReminderIntents.openTakeIntent(context, key).setFlags(0)

    private fun text(
        @StringRes id: Int,
    ) = context.getString(id)

    private fun assertOn(
        @StringRes title: Int,
    ) = compose.onNodeWithText(text(title)).assertIsDisplayed()

    private fun assertNotOn(
        @StringRes title: Int,
    ) = compose.onNodeWithText(text(title)).assertDoesNotExist()

    @Test
    fun `a reminder content intent opens its take and Back goes Home`() {
        ActivityScenario.launch<MainActivity>(reminderIntent()).use {
            assertOn(R.string.take_title)
            pressBack()
            assertOn(R.string.home_title)
        }
    }

    @Test
    fun `a recreated activity does not open the take again`() {
        ActivityScenario.launch<MainActivity>(reminderIntent()).use { scenario ->
            assertOn(R.string.take_title)
            pressBack()
            assertOn(R.string.home_title)

            scenario.recreate()
            compose.waitForIdle()
            assertOn(R.string.home_title)
            assertNotOn(R.string.take_title)
        }
    }

    @Test
    fun `a reminder tapped while the app is open opens its take above Home`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertOn(R.string.home_title)
            compose.onNodeWithContentDescription(text(R.string.home_settings)).performClick()
            assertOn(R.string.settings_title)

            scenario.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, reminderIntent()) }
            assertOn(R.string.take_title)
            pressBack()
            assertOn(R.string.home_title)
        }
    }

    @Test
    fun `the same reminder tapped again opens its take again`() {
        ActivityScenario.launch<MainActivity>(reminderIntent()).use { scenario ->
            assertOn(R.string.take_title)
            pressBack()
            assertOn(R.string.home_title)

            scenario.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, reminderIntent()) }
            assertOn(R.string.take_title)
        }
    }

    @Test
    fun `another intent while the app is open leaves it where it was`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithContentDescription(text(R.string.home_settings)).performClick()
            assertOn(R.string.settings_title)

            val summaryTap = Intent(context, MainActivity::class.java)
            scenario.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, summaryTap) }
            compose.waitForIdle()
            assertOn(R.string.settings_title)
        }
    }

    @Test
    fun `an old tap relaunched from Recents does not open the take`() {
        val intent = reminderIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitForIdle()
            assertOn(R.string.home_title)
            assertNotOn(R.string.take_title)
        }
    }

    @Test
    fun `a reminder intent holds its take as the route the Take screen reads`() {
        assertEquals(TakeRoute(medicineId = "open-take-test", plannedAt = "2026-11-23T16:00"), takeOf(reminderIntent()))
        assertNull(takeOf(Intent(context, MainActivity::class.java)))
    }

    @Test
    fun `the navigation opens the take with its arguments right above Home and says so once`() {
        val take = TakeRoute(medicineId = "open-take-test", plannedAt = "2026-11-23T16:00")
        val opened = AtomicInteger()
        lateinit var nav: NavHostController
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.setContent {
                    nav = rememberNavController()
                    AppNavHost(nav, openTake = take, onTakeOpened = { opened.incrementAndGet() })
                }
            }
            compose.waitForIdle()

            scenario.onActivity {
                assertEquals(take, nav.currentBackStackEntry?.toRoute<TakeRoute>())
                assertTrue(nav.previousBackStackEntry?.destination?.hasRoute<HomeRoute>() == true)
            }
            assertEquals(1, opened.get())
        }
    }
}
