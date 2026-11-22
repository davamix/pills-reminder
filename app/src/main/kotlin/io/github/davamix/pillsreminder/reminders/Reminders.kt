package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.actions.skip
import io.github.davamix.pillsreminder.core.actions.snooze
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/** The three actions of a reminder (RM-3). */
enum class ReminderAction { TAKEN, SNOOZE, SKIP }

/**
 * The reminders of the app, as `AppContainer` holds them: what the receivers, the activity and the debug tools call.
 * Every call ends in a [Reconciler] run, so the shade and the alarm follow the data (P5 SPEC §4.3.2).
 */
class Reminders(
    private val repository: TreatmentRepository,
    private val clock: Clock,
    private val reconciler: Reconciler,
    private val shade: ReminderShade,
    /** Lives as long as the process: the observer and the receivers' work run in it. */
    private val scope: CoroutineScope,
    private val log: ReminderLog = ReminderLog.None,
) {
    private val started = AtomicBoolean(false)

    /**
     * Prepares the shade, then reconciles on the stored data's first value and after every change (P5-D2), so an answer,
     * an edit, an end or a delete made anywhere in the app updates the shade and the alarm. The reconcile's own write is
     * a change too: the reconcile that follows finds nothing to do and writes nothing. Called once, when the app starts;
     * later calls do nothing.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        shade.prepare()
        scope.launch {
            var first = true
            repository.data.conflate().collect {
                guarded { reconcile(if (first) ReconcileTrigger.APP_START else ReconcileTrigger.DATA_CHANGED) }
                first = false
            }
        }
    }

    /** Reconciles now: for the receivers, `MainActivity` and the debug screen. */
    suspend fun reconcile(trigger: ReconcileTrigger): ReconcileResult = reconciler.reconcile(trigger)

    /**
     * Records the answer, or the snooze, through P2's and P5's helpers, then reconciles, whatever the answer gave: the
     * reminder leaves the shade, answered, snoozed or stale (P5 SPEC §4.3.5). A refused answer changes nothing.
     */
    suspend fun answer(
        action: ReminderAction,
        key: TakeKey,
    ) {
        val applied =
            guarded(default = false) {
                when (action) {
                    ReminderAction.TAKEN -> repository.markTaken(key, clock)
                    ReminderAction.SNOOZE -> repository.snooze(key, clock)
                    ReminderAction.SKIP -> repository.skip(key, clock)
                }
            }
        log.event("action ${action.name.lowercase()} ${TakeKeyCodec.encode(key)} ${if (applied) "applied" else "refused"}")
        reconcile(ReconcileTrigger.ACTION)
    }

    /**
     * For a receiver's `goAsync()`: runs [work] in the process's scope, and calls [done], the pending result's `finish()`,
     * when it ends, however it ends. A failure is logged by class only.
     */
    fun runAsync(
        done: () -> Unit,
        work: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                guarded { work() }
            } finally {
                done()
            }
        }
    }

    /** Runs [block]; an exception other than a cancellation is logged by class only, never its message (plan §3). */
    private suspend fun <T> guarded(
        default: T,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.event("error ${e::class.simpleName}")
            default
        }

    private suspend fun guarded(block: suspend () -> Unit) = guarded(Unit, block)
}
