package io.github.davamix.pillsreminder.reminders

import io.github.davamix.pillsreminder.core.model.InMemoryTreatmentRepository
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.StoredData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.TreatmentRepository
import io.github.davamix.pillsreminder.data.nowAt
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

// Fakes for the reconcile's JVM tests (P5 SPEC §4.1). They share one list of steps, so a test can check the order of
// P5-D3: write, alarm, removals, posts, summary.

/** The shade: what is in it, and every post with whether it alerted. */
class FakeShade(
    private val steps: MutableList<String> = mutableListOf(),
) : ReminderShade {
    /** The reminders in the shade, by key, in the order posted. */
    val shade = linkedMapOf<TakeKey, ReminderContent>()

    /** Every post since the last [clearPosts]: the key, and whether it alerted. */
    val posts = mutableListOf<Pair<TakeKey, Boolean>>()
    var summaryCount = 0
        private set
    var prepared = 0
        private set

    /** When set, every post throws it. */
    var failure: Exception? = null

    override fun prepare() {
        prepared++
    }

    override fun shown(): Map<TakeKey, String> = shade.mapValues { it.value.fingerprint }

    override fun post(
        content: ReminderContent,
        alert: Boolean,
    ) {
        failure?.let { throw it }
        shade[content.key] = content
        posts += content.key to alert
        steps += if (alert) "alert" else "post"
    }

    override fun remove(key: TakeKey) {
        shade.remove(key)
        steps += "remove"
    }

    override fun summary(count: Int) {
        summaryCount = count
        steps += "summary $count"
    }

    /** The user swipes the reminder away (Q2). */
    fun swipe(key: TakeKey) {
        shade.remove(key)
    }

    /** A restart or an update empties the shade (T2). */
    fun clear() = shade.clear()

    fun alerted(): List<TakeKey> = posts.filter { it.second }.map { it.first }

    fun silent(): List<TakeKey> = posts.filterNot { it.second }.map { it.first }

    fun clearPosts() = posts.clear()
}

/** The single alarm: where it is set, and whether as a retry. */
class FakeAlarm(
    private val steps: MutableList<String> = mutableListOf(),
) : WakeUpAlarm {
    var at: Instant? = null
        private set
    var retry = false
        private set

    override fun set(
        at: Instant,
        retry: Boolean,
    ) {
        this.at = at
        this.retry = retry
        steps += "alarm"
    }

    override fun cancel() {
        at = null
        retry = false
        steps += "alarm"
    }
}

/** P2's in-memory repository, counting the writes that changed the data, and failing them on demand. */
class FakeRepository(
    initial: StoredData,
    private val steps: MutableList<String> = mutableListOf(),
) : TreatmentRepository {
    private val delegate = InMemoryTreatmentRepository(initial)

    /** When set, every update throws it before changing anything, as a full disk or a refused write would. */
    var failure: Exception? = null
    var writes = 0
        private set

    override val data: Flow<StoredData> = delegate.data

    val current: PillsData get() = (delegate.data.value as StoredData.Readable).data

    override suspend fun update(transform: (PillsData) -> PillsData): PillsData {
        failure?.let { throw it }
        val before = (delegate.data.value as? StoredData.Readable)?.data
        val after = delegate.update(transform)
        if (after != before) {
            writes++
            steps += "write"
        }
        return after
    }
}

/** A clock the test moves. Its zone is the zone of [now]. */
class MutableClock(
    var now: ZonedDateTime,
) : Clock() {
    fun at(local: LocalDateTime) {
        now = nowAt(local)
    }

    override fun getZone(): ZoneId = now.zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now.withZoneSameInstant(zone))

    override fun instant(): Instant = now.toInstant()
}

/** A log that keeps its lines. */
class RecordingLog : ReminderLog {
    val lines = mutableListOf<String>()

    override fun event(line: String) {
        synchronized(lines) { lines += line }
    }
}

/** The instant of a local time in Madrid. */
fun instantAt(local: LocalDateTime): Instant = nowAt(local).toInstant()
