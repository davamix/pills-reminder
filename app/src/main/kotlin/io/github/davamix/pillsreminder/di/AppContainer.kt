package io.github.davamix.pillsreminder.di

import android.content.Context
import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.time.CurrentZoneClock
import java.time.Clock

/**
 * Holds the app's long-lived objects (plan D-3). Each phase adds only its own: P2 the clock and the repository, P3
 * the real repository in place of the in-memory one, P5 the reminders.
 */
class AppContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext

    /** The phone's clock, in its time zone at each call (SC-4). Logic reads the time only through it (plan D-6). */
    val clock: Clock = CurrentZoneClock()

    /** The treatments and answers. In memory until P3 replaces it with the DataStore repository: nothing is kept yet. */
    val repository: TreatmentRepository = InMemoryTreatmentRepository()
}
