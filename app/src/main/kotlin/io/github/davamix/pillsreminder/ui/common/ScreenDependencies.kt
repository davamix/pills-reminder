package io.github.davamix.pillsreminder.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.davamix.pillsreminder.BuildConfig
import io.github.davamix.pillsreminder.PillsReminderApp
import io.github.davamix.pillsreminder.check.AndroidReminderCheckDevice
import io.github.davamix.pillsreminder.check.CheckStore
import io.github.davamix.pillsreminder.check.ReminderCheckDevice
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.di.AppContainer
import io.github.davamix.pillsreminder.reminders.ReconcileTrigger
import kotlinx.coroutines.flow.Flow
import java.time.Clock

/**
 * What the screens' ViewModels need (P6-D2). `AppNavHost` passes the app's, from its container; UI flow tests pass an
 * in-memory repository and a fixed clock, so they never touch the real data or the real reminders.
 */
data class ScreenDependencies(
    val repository: TreatmentRepository,
    val clock: Clock,
    /** Whether the interval may be in minutes: debug builds only (plan D-15). */
    val allowMinutes: Boolean,
    /** A tick at once and at each whole minute, for the screens to work out their states again (P6-D3). */
    val minuteTicks: Flow<Unit>,
    /** What the reminder check reads from the device, and the settings screens it opens (P7-D2). */
    val checkDevice: ReminderCheckDevice,
    /** The reminder check's own record: the first launch, the confirmed HyperOS steps (P7-D1). */
    val checkStore: CheckStore,
    /** Asks the reminders to catch up now: after Android's dialog grants the notification permission (P7-D6). */
    val reconcile: () -> Unit,
) {
    companion object {
        /** The app's own: the real store, the phone's clock, its settings and its reminders. */
        fun of(container: AppContainer): ScreenDependencies =
            ScreenDependencies(
                repository = container.repository,
                clock = container.clock,
                allowMinutes = BuildConfig.DEBUG,
                minuteTicks = minuteTicks(container.clock),
                checkDevice = AndroidReminderCheckDevice(container.appContext),
                checkStore = container.checkStore,
                reconcile = {
                    val reminders = container.reminders
                    reminders.runAsync(done = {}) { reminders.reconcile(ReconcileTrigger.APP_OPEN) }
                },
            )
    }
}

/** The app's [ScreenDependencies], from the application's container. */
@Composable
fun rememberAppDependencies(): ScreenDependencies {
    val app = LocalContext.current.applicationContext as PillsReminderApp
    return remember(app) { ScreenDependencies.of(app.container) }
}
