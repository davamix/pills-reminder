package io.github.davamix.pillsreminder.di

import android.content.Context
import android.util.Log
import androidx.datastore.dataStoreFile
import io.github.davamix.pillsreminder.BuildConfig
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.time.CurrentZoneClock
import io.github.davamix.pillsreminder.data.DataStoreTreatmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/**
 * Holds the app's long-lived objects (plan D-3). Each phase adds only its own: P2 the clock, P3 the repository on the
 * store, P5 the reminders.
 */
class AppContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext

    /** The phone's clock, in its time zone at each call (SC-4). Logic reads the time only through it (plan D-6). */
    val clock: Clock = CurrentZoneClock()

    /** The store's scope. It lasts as long as the process, as the only store on its file must (P3 SPEC §4.3.4). */
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The treatments and answers, kept in `files/datastore/pills.json` (P3). A file that can't be read is kept as it
     * is, and reported in debug builds' log, with no treatment content (plan §3).
     */
    val repository: TreatmentRepository =
        DataStoreTreatmentRepository.create(
            file = appContext.dataStoreFile(STORE_FILE),
            scope = storeScope,
            onUnreadable = { reason -> if (BuildConfig.DEBUG) Log.e(STORE_TAG, "Stored data unreadable: $reason") },
        )

    private companion object {
        const val STORE_FILE = "pills.json"
        const val STORE_TAG = "PillsStore"
    }
}
