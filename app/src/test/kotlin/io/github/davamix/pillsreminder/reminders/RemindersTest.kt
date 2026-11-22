package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Answers from a reminder, the observer of the data, and the receivers' work (P5 SPEC §4.3.2, §4.3.5). */
class RemindersTest {
    private val repository = FakeRepository(StoredData.Readable(PillsData(listOf(StoreExample.treatment))))
    private val clock = MutableClock(nowAt(nov(23, 8)))
    private val shade = FakeShade()
    private val alarm = FakeAlarm()
    private val log = RecordingLog()

    private val amoxicillin0800 = TakeKey(StoreExample.amoxicillinId, nov(23, 8))
    private val atrovent0800 = TakeKey(StoreExample.atroventId, nov(23, 8))

    /** The reminders, in a scope of their own on the test's scheduler, so `advanceUntilIdle()` runs the observer. */
    private fun TestScope.reminders(): Pair<Reminders, CoroutineScope> {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        return Reminders(repository, clock, Reconciler(repository, clock, shade, alarm, log), shade, scope, log) to scope
    }

    private fun reconciles(): List<String> = log.lines.filter { it.startsWith("reconcile ") }

    @Test
    fun `Taken from a reminder records the minute it was pressed, and removes the reminder`() =
        runTest {
            val (reminders, scope) = reminders()
            reminders.reconcile(ReconcileTrigger.ALARM)
            clock.at(nov(23, 8, 3).plusSeconds(45))

            reminders.answer(ReminderAction.TAKEN, amoxicillin0800)

            assertEquals(Answer.Taken(nov(23, 8, 3)), repository.current.answers[amoxicillin0800])
            assertEquals(listOf(atrovent0800), shade.shade.keys.toList())
            assertTrue("action taken 2026-11-23T08:00|amoxicillin applied" in log.lines, log.lines.toString())
            scope.cancel()
        }

    @Test
    fun `Skip records the answer and removes the reminder`() =
        runTest {
            val (reminders, scope) = reminders()
            reminders.reconcile(ReconcileTrigger.ALARM)

            reminders.answer(ReminderAction.SKIP, atrovent0800)

            assertEquals(Answer.Skipped, repository.current.answers[atrovent0800])
            assertEquals(listOf(amoxicillin0800), shade.shade.keys.toList())
            scope.cancel()
        }

    @Test
    fun `Snooze removes the reminder, and it alerts again 10 minutes later`() =
        runTest {
            val (reminders, scope) = reminders()
            reminders.reconcile(ReconcileTrigger.ALARM)
            clock.at(nov(23, 8, 5))

            reminders.answer(ReminderAction.SNOOZE, amoxicillin0800)
            assertEquals(listOf(atrovent0800), shade.shade.keys.toList())
            assertEquals(instantAt(nov(23, 8, 10)), alarm.at, "Atrovent's repeat comes first")

            // At 08:15 the snooze has ended; Atrovent's 08:10 repeat, not yet played, plays now too.
            clock.at(nov(23, 8, 15))
            shade.clearPosts()
            reminders.reconcile(ReconcileTrigger.ALARM)
            assertEquals(listOf(amoxicillin0800, atrovent0800), shade.alerted())
            assertTrue(amoxicillin0800 in shade.shade)
            scope.cancel()
        }

    @Test
    fun `a refused answer changes nothing, and the reconcile still removes the stale reminder`() =
        runTest {
            val (reminders, scope) = reminders()
            reminders.reconcile(ReconcileTrigger.ALARM)
            // The phone missed the 16:00 alarm: the 08:00 reminder is still in the shade, but its take is Missed.
            clock.at(nov(23, 16, 1))
            val answers = repository.current.answers

            reminders.answer(ReminderAction.TAKEN, amoxicillin0800)

            assertEquals(answers, repository.current.answers)
            assertFalse(amoxicillin0800 in shade.shade)
            assertTrue("action taken 2026-11-23T08:00|amoxicillin refused" in log.lines, log.lines.toString())
            scope.cancel()
        }

    @Test
    fun `the observer reconciles on the first data, and after a change made anywhere through the repository`() =
        runTest {
            val (reminders, scope) = reminders()

            reminders.start()
            advanceUntilIdle()
            assertEquals(listOf(amoxicillin0800, atrovent0800), shade.shade.keys.toList())
            assertEquals(1, shade.prepared)

            // An answer in the app, as P6's Take screen or P7's home screen gives it (RM-7, P5-D2).
            clock.at(nov(23, 8, 4))
            repository.markTaken(amoxicillin0800, clock)
            advanceUntilIdle()
            assertEquals(listOf(atrovent0800), shade.shade.keys.toList())
            assertTrue("reconcile data_changed at ${clock.instant()}" in log.lines, log.lines.toString())
            scope.cancel()
        }

    @Test
    fun `the reconcile's own write leads to one more reconcile, which writes nothing`() =
        runTest {
            val (reminders, scope) = reminders()

            reminders.start()
            advanceUntilIdle()

            assertEquals(listOf("reconcile app_start", "reconcile data_changed"), reconciles().map { it.substringBefore(" at ") })
            assertEquals(1, repository.writes)
            scope.cancel()
        }

    @Test
    fun `starting twice prepares the shade and observes the data once`() =
        runTest {
            val (reminders, scope) = reminders()

            reminders.start()
            reminders.start()
            advanceUntilIdle()

            assertEquals(1, shade.prepared)
            assertEquals(1, reconciles().count { it.startsWith("reconcile app_start") })
            scope.cancel()
        }

    @Test
    fun `runAsync finishes when the work ends, also when it throws, and logs the class only`() =
        runTest {
            val (reminders, scope) = reminders()
            var done = 0

            reminders.runAsync(done = { done++ }) { reminders.reconcile(ReconcileTrigger.ALARM) }
            reminders.runAsync(done = { done++ }) { throw IllegalStateException("Amoxicillin, 1 capsule") }
            advanceUntilIdle()

            assertEquals(2, done)
            assertTrue("error IllegalStateException" in log.lines, log.lines.toString())
            assertFalse(log.lines.any { "Amoxicillin" in it })
            scope.cancel()
        }
}
