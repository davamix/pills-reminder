package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.model.Answer
import io.github.davamix.pillsreminder.core.model.Interval
import io.github.davamix.pillsreminder.core.model.Medicine
import io.github.davamix.pillsreminder.core.model.MedicineId
import io.github.davamix.pillsreminder.core.model.PillsData
import io.github.davamix.pillsreminder.core.model.TakeKey
import io.github.davamix.pillsreminder.core.model.medicine
import io.github.davamix.pillsreminder.core.time.dueAt
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Longer than any daylight saving change, so a take planned this long before now on the local clock is past. */
private const val DUE_SEARCH_MARGIN_HOURS = 3L

/**
 * A medicine's takes: those of its earlier and current schedules, cut at its treatment's end, with their answers and
 * states (SPEC §4.3.3, §4.3.4).
 *
 * - Each earlier schedule contributes its takes planned after the previous one's `until`, up to its own `until`; the
 *   current schedule, its takes after the last `until`.
 * - If the treatment was ended, only takes planned at or before [treatmentEndedAt] exist, plus any answered take after
 *   it (TR-11).
 * - Answers that don't belong to one of these takes are ignored.
 */
class MedicineTakes(
    val medicine: Medicine,
    private val treatmentEndedAt: LocalDateTime?,
    answers: Map<LocalDateTime, Answer>,
) {
    private val segments: List<Segment> =
        medicine.earlierSchedules.mapIndexed { index, earlier ->
            Segment(
                PlannedTakes(earlier.schedule),
                after = medicine.earlierSchedules.getOrNull(index - 1)?.until,
                until = earlier.until,
                endedAt = treatmentEndedAt,
            )
        } +
            Segment(
                PlannedTakes(medicine.schedule),
                after = medicine.earlierSchedules.lastOrNull()?.until,
                until = null,
                endedAt = treatmentEndedAt,
            )

    /** The answers of takes that exist, by planned time. */
    val answers: Map<LocalDateTime, Answer> = answers.filterKeys { segmentOf(it) != null }

    /** Answered takes after the treatment's end, which exist because they are answered. All come after every other take. */
    private val answeredAfterEnd: List<LocalDateTime> =
        if (treatmentEndedAt == null) {
            emptyList()
        } else {
            this.answers.keys
                .filter { it > treatmentEndedAt }
                .sorted()
        }

    /** How many takes there are, answered or not; `null` while the current schedule has no end and the treatment runs. */
    val count: Long? =
        if (segments.any { it.end == null }) {
            null
        } else {
            segments.sumOf { it.size } + answeredAfterEnd.size
        }

    /** The first take, or `null` when there is none (the treatment ended before it). */
    val first: LocalDateTime? = segments.firstNotNullOfOrNull { it.firstTake() } ?: answeredAfterEnd.firstOrNull()

    /** The last take, or `null` for no end (or no take at all). */
    val last: LocalDateTime? =
        if (count == null) null else answeredAfterEnd.lastOrNull() ?: segments.asReversed().firstNotNullOfOrNull { it.lastTake() }

    /** Whether a take is planned at [plannedAt]. */
    operator fun contains(plannedAt: LocalDateTime): Boolean =
        segmentOf(plannedAt) != null && (treatmentEndedAt == null || plannedAt <= treatmentEndedAt || plannedAt in answers)

    /** The take planned at [plannedAt] as it stands at [now], or `null` if there is no such take. */
    fun take(
        plannedAt: LocalDateTime,
        now: ZonedDateTime,
    ): Take? = if (plannedAt in this) build(plannedAt, now) else null

    /** The takes planned in `[from, until)`, in order, as they stand at [now]. */
    fun takes(
        from: LocalDateTime,
        until: LocalDateTime,
        now: ZonedDateTime,
    ): Sequence<Take> =
        generateSequence(if (from in this) from else nextAfter(from)) { nextAfter(it) }
            .takeWhile { it < until }
            .map { build(it, now) }

    /** How many takes are answered Taken or Skipped (HM-3). Missed takes don't count. */
    val answeredCount: Int get() = answers.size

    /**
     * The medicine's next take (HM-1, TK-4): its Due take if it has one, or else its first unanswered take still to
     * come, or `null` when none is left.
     */
    fun nextTake(now: ZonedDateTime): Take? {
        val latest = latestDue(now)
        if (latest != null) build(latest, now).takeIf { it.state == TakeState.DUE }?.let { return it }
        return firstUnansweredAfter(latest)?.let { build(it, now) }
    }

    /**
     * Whether the take planned at [plannedAt] can be marked Taken before it is due (TK-4): it is the next take, it is
     * Pending, and no take of the medicine is already answered ahead of its time. So one take ahead at most.
     */
    fun canMarkEarly(
        plannedAt: LocalDateTime,
        now: ZonedDateTime,
    ): Boolean {
        val next = nextTake(now) ?: return false
        return next.key.plannedAt == plannedAt &&
            next.state == TakeState.PENDING &&
            answers.keys.none { it.dueAt(now.zone) > now.toInstant() }
    }

    /** The answered take with the latest planned time (TK-5), or `null` when nothing is answered. */
    fun lastAnswered(now: ZonedDateTime): Take? = answers.keys.maxOrNull()?.let { build(it, now) }

    /**
     * The earliest moment after [now] at which a take changes state by itself: the Due take becomes Missed, or the first
     * unanswered take still to come falls due. `null` when nothing is left to change. P4 wakes up at it; P7 refreshes.
     */
    fun nextStateChange(now: ZonedDateTime): Instant? {
        val latest = latestDue(now)
        val missed =
            latest
                ?.let { build(it, now) }
                ?.takeIf { it.state == TakeState.DUE }
                ?.missedAt
                ?.dueAt(now.zone)
        val due = firstUnansweredAfter(latest)?.dueAt(now.zone)
        return listOfNotNull(missed, due).filter { it > now.toInstant() }.minOrNull()
    }

    /**
     * The latest take that has fallen due by [now], or `null`. Only it can be Due: every take before it is answered or
     * Missed, because a take is Missed by the time the next one falls due.
     *
     * Starts from the last take planned more than [DUE_SEARCH_MARGIN_HOURS] before now on the local clock, which has
     * fallen due whatever the daylight saving change, and steps forward comparing instants (SPEC §4.3.2).
     */
    private fun latestDue(now: ZonedDateTime): LocalDateTime? {
        val instant = now.toInstant()
        var latest = latestAtOrBefore(now.toLocalDateTime().minusHours(DUE_SEARCH_MARGIN_HOURS))
        var next = if (latest == null) first else nextAfter(latest)
        while (next != null && next.dueAt(now.zone) <= instant) {
            latest = next
            next = nextAfter(next)
        }
        return latest
    }

    /** The first unanswered take after [time], or from the first take when [time] is `null`. */
    private fun firstUnansweredAfter(time: LocalDateTime?): LocalDateTime? {
        var next = if (time == null) first else nextAfter(time)
        while (next != null && next in answers) next = nextAfter(next)
        return next
    }

    /** The first take planned strictly after [time], or `null`. */
    internal fun nextAfter(time: LocalDateTime): LocalDateTime? =
        segments.firstNotNullOfOrNull { it.firstAfter(time) } ?: answeredAfterEnd.firstOrNull { it > time }

    /** The last take planned at or before [time], or `null`. */
    internal fun latestAtOrBefore(time: LocalDateTime): LocalDateTime? =
        answeredAfterEnd.lastOrNull { it <= time } ?: segments.asReversed().firstNotNullOfOrNull { it.lastAtOrBefore(time) }

    /**
     * When the take planned at [plannedAt] becomes Missed if unanswered (TK-3, TR-11): the earliest of one interval of
     * its own schedule after it, the medicine's next take, and the treatment's end if that came at or after the take.
     */
    internal fun missedAt(plannedAt: LocalDateTime): LocalDateTime {
        val interval = checkNotNull(segmentOf(plannedAt)) { "No take at $plannedAt" }.interval
        return listOfNotNull(
            plannedAt.plusMinutes(interval.minutes),
            nextAfter(plannedAt),
            treatmentEndedAt?.takeIf { it >= plannedAt },
        ).min()
    }

    private fun build(
        plannedAt: LocalDateTime,
        now: ZonedDateTime,
    ): Take {
        val answer = answers[plannedAt]
        val missedAt = missedAt(plannedAt)
        val state =
            when {
                answer is Answer.Taken -> TakeState.TAKEN
                answer == Answer.Skipped -> TakeState.SKIPPED
                plannedAt.dueAt(now.zone) > now.toInstant() -> TakeState.PENDING
                missedAt.dueAt(now.zone) <= now.toInstant() -> TakeState.MISSED
                else -> TakeState.DUE
            }
        return Take(TakeKey(medicine.id, plannedAt), state, (answer as? Answer.Taken)?.at, missedAt)
    }

    /** The schedule whose window holds [plannedAt] as one of its planned times, ignoring the treatment's end. */
    private fun segmentOf(plannedAt: LocalDateTime): Segment? = segments.firstOrNull { it.holds(plannedAt) }

    /** One schedule's takes, in its window `(after, until]`, and cut at the treatment's end. */
    private class Segment(
        private val takes: PlannedTakes,
        after: LocalDateTime?,
        until: LocalDateTime?,
        endedAt: LocalDateTime?,
    ) {
        val interval: Interval = takes.schedule.interval

        private val windowStart: Long = after?.let { takes.boundAfter(it) } ?: 0
        private val windowEnd: Long? = until?.let { takes.boundAfter(it) } ?: takes.count

        /** The first index, inclusive. */
        val start: Long = windowStart

        /** The end index, exclusive; `null` for no end. */
        val end: Long? = listOfNotNull(windowEnd, endedAt?.let { takes.boundAfter(it) }).minOrNull()

        val size: Long get() = maxOf(0, checkNotNull(end) - start)

        /** Whether [time] is one of this schedule's planned times inside its window. */
        fun holds(time: LocalDateTime): Boolean {
            val index = takes.indexOf(time) ?: return false
            return index >= windowStart && (windowEnd == null || index < windowEnd)
        }

        fun firstTake(): LocalDateTime? = if (end == null || start < end) takes[start] else null

        fun lastTake(): LocalDateTime? = end?.let { if (it > start) takes[it - 1] else null }

        fun firstAfter(time: LocalDateTime): LocalDateTime? {
            val index = maxOf(start, takes.indexOfFirstAfter(time) ?: return null)
            return if (end == null || index < end) takes[index] else null
        }

        fun lastAtOrBefore(time: LocalDateTime): LocalDateTime? {
            var index = (takes.indexOfFirstAfter(time) ?: checkNotNull(takes.count)) - 1
            if (end != null) index = minOf(index, end - 1)
            return if (index >= start) takes[index] else null
        }

        /** The index of the first take after [time], or the count when none is left. */
        private fun PlannedTakes.boundAfter(time: LocalDateTime): Long = indexOfFirstAfter(time) ?: checkNotNull(count)
    }
}

/** The takes of the medicine with this ID, or `null` if there is none. */
fun PillsData.takesOf(medicineId: MedicineId): MedicineTakes? {
    val (treatment, medicine) = medicine(medicineId) ?: return null
    val answers = answers.filterKeys { it.medicineId == medicineId }.mapKeys { it.key.plannedAt }
    return MedicineTakes(medicine, treatment.endedAt, answers)
}

/** The take with this key as it stands at [now], or `null` if there is none. */
fun PillsData.take(
    key: TakeKey,
    now: ZonedDateTime,
): Take? = takesOf(key.medicineId)?.take(key.plannedAt, now)
