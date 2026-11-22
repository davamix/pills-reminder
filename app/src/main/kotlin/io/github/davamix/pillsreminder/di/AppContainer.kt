package io.github.davamix.pillsreminder.di

import android.content.Context
import android.util.Log
import androidx.datastore.dataStoreFile
import io.github.davamix.pillsreminder.BuildConfig
import io.github.davamix.pillsreminder.check.CheckStore
import io.github.davamix.pillsreminder.check.DataStoreCheckStore
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.time.CurrentZoneClock
import io.github.davamix.pillsreminder.data.DataStoreTreatmentRepository
import io.github.davamix.pillsreminder.reminders.AlarmClockWakeUp
import io.github.davamix.pillsreminder.reminders.AndroidReminderShade
import io.github.davamix.pillsreminder.reminders.FileReminderLog
import io.github.davamix.pillsreminder.reminders.Reconciler
import io.github.davamix.pillsreminder.reminders.ReminderLog
import io.github.davamix.pillsreminder.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
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

    /** Reminder events, IDs and times only: logcat and `files/reminders-log.txt` in debug builds, nothing in release. */
    val reminderLog: ReminderLog =
        if (BuildConfig.DEBUG) FileReminderLog(File(appContext.filesDir, FileReminderLog.FILE_NAME), clock) else ReminderLog.None

    /** The reminders' scope: the data's observer and the receivers' work. It lasts as long as the process. */
    private val remindersScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The reminders (P5): the reconcile on the real store, the notification shade and the alarm clock. `PillsReminderApp`
     * starts them; the receivers and `MainActivity` call them.
     */
    val reminders: Reminders =
        AndroidReminderShade(appContext).let { shade ->
            val alarm = AlarmClockWakeUp(appContext, log = reminderLog)
            Reminders(repository, clock, Reconciler(repository, clock, shade, alarm, reminderLog), shade, remindersScope, reminderLog)
        }

    /** The reminder check's scope: its store's. It lasts as long as the process (P7). */
    private val checkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The reminder check's own record, `files/datastore/reminder-check.json`, apart from the treatments (P7-D1): whether
     * the first launch is done, and which HyperOS steps the user has confirmed.
     */
    val checkStore: CheckStore = DataStoreCheckStore.create(appContext.dataStoreFile(DataStoreCheckStore.FILE_NAME), checkScope)

    private companion object {
        const val STORE_FILE = "pills.json"
        const val STORE_TAG = "PillsStore"
    }
}
