package io.github.davamix.pillsreminder.di

import android.content.Context

/**
 * Holds the app's long-lived objects (plan D-3). Each phase adds only its own: P3 the repository, P5 the reminders.
 */
class AppContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext
}
