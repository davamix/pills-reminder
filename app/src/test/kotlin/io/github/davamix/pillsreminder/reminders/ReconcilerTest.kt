package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.endTreatment
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.actions.snooze
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.toDraft
import io.github.davamix.pillsreminder.core.reminders.TakeReminder
import io.github.davamix.pillsreminder.data.MADRID
import io.github.davamix.pillsreminder.data.StoreExample
import io.github.davamix.pillsreminder.data.nov
import io.github.davamix.pillsreminder.data.nowAt
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime

/** The reconcile with fakes (P5 SPEC §4.3.1, §4.3.11), on the §4.4 example in Madrid. */
class ReconcilerTest {
    private val steps = mutableListOf<String>()
    private val example = PillsData(listOf(StoreExample.treatment))
    private val repository = FakeRepository(StoredData.Readable(example), steps)
    private val clock = MutableClock(nowAt(nov(23, 7)))
    private val shade = FakeShade(steps)
    private val alarm = FakeAlarm(steps)
    private val log = RecordingLog()
    private val reconciler = Reconciler(repository, clock, shade, alarm, log)

    private val amoxicillin0800 = TakeKey(StoreExample.amoxicillinId, nov(23, 8))
    private val atrovent0800 = TakeKey(StoreExample.atroventId, nov(23, 8))
    private val amoxicillin1600 = TakeKey(StoreExample.amoxicillinId, nov(23, 16))
    private val atrovent1600 = TakeKey(StoreExample.atroventId, nov(23, 16))

    /** Moves the clock to [local], reconciles, and returns what alerted; the posts are cleared for the next step. */
    private suspend fun at(
        local: LocalDateTime,
        trigger: ReconcileTrigger = ReconcileTrigger.ALARM,
    ): List<TakeKey> {
        clock.at(local)
        shade.clearPosts()
        assertTrue(reconciler.reconcile(trigger) is ReconcileResult.Applied, "the reconcile at $local")
        return shade.alerted()
    }

    @Test
    fun `the 4-4 example on Monday alerts, repeats, snoozes, comes back after a restart and removes, as SPEC 4-3-11 says`() =
        runTest {
            assertEquals(emptyList<TakeKey>(), at(nov(23, 7), ReconcileTrigger.APP_START))
            assertEquals(instantAt(nov(23, 8)), alarm.at)

            // 08:00: both takes fall due, and alert (RM-4).
            assertEquals(listOf(amoxicillin0800, atrovent0800), at(nov(23, 8)))
            assertEquals(2, shade.summaryCount)
            assertEquals(instantAt(nov(23, 8, 10)), alarm.at)

            // 08:02: Taken on Atrovent's, from its reminder. Amoxicillin's stays, and isn't posted again.
            clock.at(nov(23, 8, 2))
            assertTrue(repository.markTaken(atrovent0800, clock))
            assertEquals(emptyList<TakeKey>(), at(nov(23, 8, 2), ReconcileTrigger.ACTION))
            assertEquals(listOf(amoxicillin0800), shade.shade.keys.toList())
            assertEquals(emptyList<Pair<TakeKey, Boolean>>(), shade.posts)
            assertEquals(1, shade.summaryCount)

            // 08:10, 08:20, 08:30: the repeats (RM-5); then the alarm waits for 16:00.
            for (minute in listOf(10, 20, 30)) assertEquals(listOf(amoxicillin0800), at(nov(23, 8, minute)), "at 08:$minute")
            assertEquals(instantAt(nov(23, 16)), alarm.at)

            // 08:45: Snooze. It leaves the shade, and comes back at 08:55 with a new round (RM-3).
            clock.at(nov(23, 8, 45))
            assertTrue(repository.snooze(amoxicillin0800, clock))
            at(nov(23, 8, 45), ReconcileTrigger.ACTION)
            assertTrue(shade.shade.isEmpty())
            assertEquals(0, shade.summaryCount)
            assertEquals(instantAt(nov(23, 8, 55)), alarm.at)
            assertEquals(listOf(amoxicillin0800), at(nov(23, 8, 55)))
            for (minute in listOf(9 * 60 + 5, 9 * 60 + 15, 9 * 60 + 25)) {
                assertEquals(listOf(amoxicillin0800), at(nov(23, 0, 0).plusMinutes(minute.toLong())))
            }

            // 12:00: the phone restarts, which empties the shade; unlocked at 12:05. The reminder comes back silently.
            shade.clear()
            assertEquals(emptyList<TakeKey>(), at(nov(23, 12, 5), ReconcileTrigger.BOOT))
            assertEquals(listOf(amoxicillin0800), shade.silent())
            assertEquals(instantAt(nov(23, 16)), alarm.at)

            // 16:00: the 08:00 take is Missed, and its reminder goes; both 16:00 takes alert (TK-3, RM-7).
            assertEquals(listOf(amoxicillin1600, atrovent1600), at(nov(23, 16)))
            assertEquals(listOf(amoxicillin1600, atrovent1600), shade.shade.keys.toList())
            assertEquals(instantAt(nov(23, 16, 10)), alarm.at)
        }

    @Test
    fun `a reconcile writes, then sets the alarm, then removes, then posts`() =
        runTest {
            at(nov(23, 8))
            steps.clear()

            // 16:00: both 08:00 takes are Missed, and both 16:00 takes fall due.
            at(nov(23, 16))

            assertEquals(listOf("write", "alarm", "remove", "remove", "alert", "alert", "summary 2"), steps)
        }

    @Test
    fun `a second reconcile at the same moment writes nothing, posts nothing and alerts nothing`() =
        runTest {
            at(nov(23, 8))
            val writes = repository.writes
            val data = repository.current

            assertEquals(emptyList<TakeKey>(), at(nov(23, 8)))

            assertEquals(writes, repository.writes)
            assertEquals(data, repository.current)
            assertEquals(emptyList<Pair<TakeKey, Boolean>>(), shade.posts)
            assertEquals(instantAt(nov(23, 8, 10)), alarm.at)
        }

    @Test
    fun `between alerts, a reminder in the shade isn't posted again`() =
        runTest {
            at(nov(23, 8))

            for (minute in 1..9) at(nov(23, 8, minute))

            assertEquals(emptyList<Pair<TakeKey, Boolean>>(), shade.posts)
        }

    @Test
    fun `an edit of the name is posted again silently, without an alert`() =
        runTest {
            at(nov(23, 8))
            clock.at(nov(23, 8, 5))
            val draft = StoreExample.treatment.toDraft()
            val amoxicillin = StoreExample.amoxicillinId
            val renamed = draft.copy(medicines = draft.medicines.map { if (it.id == amoxicillin) it.copy(name = "Amoxil") else it })
            repository.update { it.saveTreatment(renamed, clock.now) }

            at(nov(23, 8, 5), ReconcileTrigger.DATA_CHANGED)

            assertEquals(listOf(amoxicillin0800 to false), shade.posts)
            assertEquals("Amoxil", shade.shade[amoxicillin0800]?.medicine)
        }

    @Test
    fun `a reminder swiped away comes back silently at the next reconcile`() =
        runTest {
            at(nov(23, 8))
            shade.swipe(amoxicillin0800)

            at(nov(23, 8, 5), ReconcileTrigger.APP_OPEN)

            assertEquals(listOf(amoxicillin0800 to false), shade.posts)
            assertTrue(amoxicillin0800 in shade.shade)
        }

    @Test
    fun `a swiped reminder comes back with sound when its repeat is due`() =
        runTest {
            at(nov(23, 8))
            shade.swipe(amoxicillin0800)

            // Atrovent's reminder, still in the shade, repeats at 08:10 too.
            assertEquals(listOf(amoxicillin0800, atrovent0800), at(nov(23, 8, 10)))
            assertTrue(amoxicillin0800 in shade.shade)
        }

    @Test
    fun `ending the treatment removes its reminders, and leaves nothing to wake for`() =
        runTest {
            at(nov(23, 8))
            clock.at(nov(23, 8, 5))
            repository.update { checkNotNull(it.endTreatment(StoreExample.treatmentId, clock.now)) }

            at(nov(23, 8, 5), ReconcileTrigger.DATA_CHANGED)

            assertTrue(shade.shade.isEmpty())
            assertEquals(0, shade.summaryCount)
            assertNull(alarm.at)
        }

    @Test
    fun `deleting the treatment removes its reminders and their records, and cancels the alarm`() =
        runTest {
            at(nov(23, 8))
            repository.update { checkNotNull(it.deleteTreatment(StoreExample.treatmentId)) }

            at(nov(23, 8, 5), ReconcileTrigger.DATA_CHANGED)

            assertTrue(shade.shade.isEmpty())
            assertNull(alarm.at)
            assertEquals(emptyMap<TakeKey, TakeReminder>(), repository.current.reminders.takes)
        }

    @Test
    fun `a reminder the plan doesn't hold is removed, such as a removed medicine's`() =
        runTest {
            val stray = TakeKey(MedicineId("removed"), nov(23, 8))
            shade.post(checkNotNull(example.reminderContent(amoxicillin0800, MADRID)).copy(key = stray), alert = false)

            at(nov(23, 7, 30), ReconcileTrigger.APP_START)

            assertFalse(stray in shade.shade)
        }

    @Test
    fun `unreadable data changes nothing on the phone`() =
        runTest {
            val unreadable = FakeRepository(StoredData.Unreadable)
            val reconciler = Reconciler(unreadable, clock, shade, alarm, log)
            shade.post(checkNotNull(example.reminderContent(amoxicillin0800, MADRID)), alert = false)
            alarm.set(instantAt(nov(23, 8, 10)))
            clock.at(nov(23, 8, 5))
            shade.clearPosts()

            assertEquals(ReconcileResult.Unreadable, reconciler.reconcile(ReconcileTrigger.ALARM))

            assertEquals(listOf(amoxicillin0800), shade.shade.keys.toList())
            assertEquals(emptyList<Pair<TakeKey, Boolean>>(), shade.posts)
            assertEquals(instantAt(nov(23, 8, 10)), alarm.at)
        }

    @Test
    fun `a failed write applies nothing, and tries again 5 minutes later, when the alerts still play`() =
        runTest {
            repository.failure = IOException("disk full")
            clock.at(nov(23, 8))

            assertEquals(ReconcileResult.Failed, reconciler.reconcile(ReconcileTrigger.ALARM))
            assertTrue(shade.shade.isEmpty())
            assertEquals(instantAt(nov(23, 8, 5)), alarm.at)
            assertTrue(alarm.retry)

            repository.failure = null
            assertEquals(listOf(amoxicillin0800, atrovent0800), at(nov(23, 8, 5), ReconcileTrigger.RETRY))
            assertFalse(alarm.retry)
        }

    @Test
    fun `a failure is logged by its class only, never its message`() =
        runTest {
            repository.failure = IllegalStateException("Amoxicillin, 1 capsule, Example")
            clock.at(nov(23, 8))

            assertEquals(ReconcileResult.Failed, reconciler.reconcile(ReconcileTrigger.ALARM))

            assertTrue(log.lines.any { it.startsWith("failed IllegalStateException") }, log.lines.toString())
            assertFalse(log.lines.any { "Amoxicillin" in it || "capsule" in it || "Example" in it })
        }

    @Test
    fun `a failure while posting sets the alarm to try again, and the records are kept`() =
        runTest {
            shade.failure = SecurityException("not allowed")
            clock.at(nov(23, 8))

            assertEquals(ReconcileResult.Failed, reconciler.reconcile(ReconcileTrigger.ALARM))

            assertEquals(instantAt(nov(23, 8, 5)), alarm.at)
            assertTrue(alarm.retry)
            assertTrue(amoxicillin0800 in repository.current.reminders.takes)
        }

    @Test
    fun `an alert's lateness is logged against when it was due, with IDs and times only`() =
        runTest {
            at(nov(23, 8, 25))

            val line = log.lines.single { it.startsWith("alert 2026-11-23T08:00|amoxicillin") }
            assertTrue(line.endsWith("due=2026-11-23T07:00:00Z at=2026-11-23T07:25:00Z late=1500000ms"), line)
            at(nov(23, 8, 35))
            assertTrue(log.lines.any { it.startsWith("alert 2026-11-23T08:00|amoxicillin due=2026-11-23T07:35:00Z") })
            for (text in StoreExample.content) assertFalse(log.lines.any { text in it }, "the log holds $text")
        }

    @Test
    fun `a reconcile that changes nothing in the data still applies the plan`() =
        runTest {
            at(nov(23, 8))
            shade.clear()
            val writes = repository.writes

            at(nov(23, 8, 5), ReconcileTrigger.APP_OPEN)

            assertEquals(writes, repository.writes)
            assertEquals(listOf(amoxicillin0800, atrovent0800), shade.silent())
            assertEquals(2, shade.summaryCount)
        }

    @Test
    fun `alerting twice at one moment never happens across reconciles in a row, and the records are stored`() =
        runTest {
            at(nov(23, 8))

            val records = repository.current.reminders.takes
            assertEquals(TakeReminder.Alerted(1, Instant.parse("2026-11-23T07:00:00Z")), records[amoxicillin0800])
            assertEquals(emptyList<TakeKey>(), at(nov(23, 8, 0)))
        }
}
