package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.core.model.UnreadableDataException
import io.github.davamix.pillsreminder.core.reminders.ReminderPlan
import io.github.davamix.pillsreminder.core.reminders.ReminderPlanner
import io.github.davamix.pillsreminder.core.reminders.ReminderState
import io.github.davamix.pillsreminder.core.reminders.TakeReminder
import io.github.davamix.pillsreminder.core.time.dueAt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** Why a reconcile runs (P5 SPEC §4.3.2). Only the debug log uses it. */
enum class ReconcileTrigger {
    APP_START,
    APP_OPEN,
    DATA_CHANGED,
    ALARM,
    ACTION,
    BOOT,
    PACKAGE_REPLACED,
    TIME_SET,
    TIMEZONE_CHANGED,
    RETRY,
    DEBUG,
}

/** What a reconcile did. */
sealed interface ReconcileResult {
    /** The plan was stored and applied. */
    data class Applied(
        val plan: ReminderPlan,
    ) : ReconcileResult

    /** The stored data can't be read: nothing was planned or changed, the shade and the alarm included (Q1). */
    data object Unreadable : ReconcileResult

    /** The write, or applying the plan, failed: an alarm tries again in [Reconciler.RETRY_AFTER] (Q1). */
    data object Failed : ReconcileResult
}

/**
 * Applies the reminder planner's plan to the phone (P5 SPEC §4.3.1, plan §2.3): one reconcile at a time, the plan
 * worked out inside the store's update on the latest data and its records stored, then the alarm, the removals, the
 * posts and the summary. Running it twice changes nothing (D-7). One instance per process, in `AppContainer`.
 */
class Reconciler(
    private val repository: TreatmentRepository,
    private val clock: Clock,
    private val shade: ReminderShade,
    private val alarm: WakeUpAlarm,
    private val log: ReminderLog = ReminderLog.None,
) {
    private val lock = Mutex()

    /** Runs the reconcile for [trigger], after any other that is running, and says what it did. */
    suspend fun reconcile(trigger: ReconcileTrigger): ReconcileResult =
        lock.withLock {
            val now = ZonedDateTime.now(clock)
            log.event("reconcile ${trigger.name.lowercase()} at ${now.toInstant()}")
            var plan: ReminderPlan? = null
            var before = ReminderState()
            val data =
                try {
                    repository.update { current ->
                        // The transform may run again: keep the plan of its last run (P4 SPEC §4.3.7).
                        val planned = ReminderPlanner.plan(current, current.reminders, now)
                        plan = planned
                        before = current.reminders
                        current.copy(reminders = planned.recorded)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: UnreadableDataException) {
                    log.event("unreadable: nothing changed")
                    return@withLock ReconcileResult.Unreadable
                } catch (e: Exception) {
                    // A failed write (IOException), the store's write check (IllegalStateException, P3-D3), or a bug.
                    // The alerts weren't recorded, so applying the plan would play them again.
                    return@withLock failed(e, now.toInstant(), next = null)
                }
            val applied = checkNotNull(plan) { "The update ran without its transform" }
            try {
                apply(applied, data, before, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock failed(e, now.toInstant(), applied.nextWakeUp)
            }
            ReconcileResult.Applied(applied)
        }

    /** Logs the failure by class only, never its message, and sets the retry: at the next wake-up if it comes sooner. */
    private fun failed(
        e: Exception,
        now: Instant,
        next: Instant?,
    ): ReconcileResult {
        val retryAt = now + RETRY_AFTER
        val at = if (next != null && next < retryAt) next else retryAt
        log.event("failed ${e::class.simpleName}: retry at $at")
        alarm.set(at, retry = at == retryAt)
        return ReconcileResult.Failed
    }

    /** Steps 4 and 5 of P5 SPEC §4.3.1: the alarm first (P5-D3), then the shade. */
    private fun apply(
        plan: ReminderPlan,
        data: PillsData,
        before: ReminderState,
        now: ZonedDateTime,
    ) {
        val next = plan.nextWakeUp
        if (next != null) alarm.set(next) else alarm.cancel()

        val shown = shade.shown()
        for (key in plan.removals(shown.keys)) {
            shade.remove(key)
            log.event("remove ${TakeKeyCodec.encode(key)}")
        }
        for (reminder in plan.reminders) {
            val content = checkNotNull(data.reminderContent(reminder.key, now.zone)) { "A planned reminder has no medicine" }
            when {
                reminder.alert -> {
                    shade.post(content, alert = true)
                    val due = dueAt(reminder.key, before.takes[reminder.key], now)
                    val late = Duration.between(due, now.toInstant()).toMillis()
                    log.event("alert ${TakeKeyCodec.encode(reminder.key)} due=$due at=${now.toInstant()} late=${late}ms")
                }

                // Silent: only when missing from the shade, or its text changed (P5-D5).
                shown[reminder.key] != content.fingerprint -> {
                    shade.post(content, alert = false)
                    log.event("post ${TakeKeyCodec.encode(reminder.key)} silently")
                }
            }
        }
        shade.summary(plan.reminders.size)
        log.event("alarm ${next ?: "none"}")
    }

    /** When an alert was due (P5 SPEC §4.3.10): the take fell due, its snooze ended, or its repeat came round. */
    private fun dueAt(
        key: TakeKey,
        record: TakeReminder?,
        now: ZonedDateTime,
    ): Instant =
        when (record) {
            null -> key.plannedAt.dueAt(now.zone)
            is TakeReminder.Snoozed -> record.until
            is TakeReminder.Alerted -> record.lastAt + ReminderPlanner.REPEAT_AFTER
        }

    companion object {
        /** How long after a failed reconcile the alarm tries again (Q1). */
        val RETRY_AFTER: Duration = Duration.ofMinutes(5)
    }
}
