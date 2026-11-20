package io.github.davamix.pillsreminder.core.schedule

import io.github.davamix.pillsreminder.core.model.Schedule
import io.github.davamix.pillsreminder.core.model.minutes
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The planned takes of one schedule on their own: no answers, no edits, no end (spec §4.3 rules 1–4 and 6).
 *
 * Take `k` is at `firstTake + k × interval`, worked out directly, so a medicine with no end costs the same after ten
 * years as on its first day. The arithmetic is on [LocalDateTime] in whole minutes, which has no time zone: 08:00 plus
 * 8 hours is 16:00, also on a daylight saving day (SC-4).
 */
class PlannedTakes(
    val schedule: Schedule,
) {
    private val first = schedule.firstTake
    private val step = schedule.interval.minutes

    /** How many takes there are: the period divided by the interval, rounded up (rule 3). `null`: no end. */
    val count: Long? = schedule.duration.minutes?.let { period -> (period + step - 1) / step }

    /** The last take, or `null` for no end. */
    val last: LocalDateTime? = count?.let { first.plusMinutes((it - 1) * step) }

    /** Take [index], from 0. */
    operator fun get(index: Long): LocalDateTime {
        require(index >= 0 && (count == null || index < count)) { "No take $index: the schedule has $count" }
        return first.plusMinutes(index * step)
    }

    /** The index of the first take planned strictly after [time], or `null` when none is left. */
    fun indexOfFirstAfter(time: LocalDateTime): Long? {
        val index = if (time < first) 0 else ChronoUnit.MINUTES.between(first, time) / step + 1
        return index.takeIf { count == null || it < count }
    }

    /** The index of the take planned exactly at [time], or `null` if no take is. */
    fun indexOf(time: LocalDateTime): Long? {
        if (time < first || time.second != 0 || time.nano != 0) return null
        val minutes = ChronoUnit.MINUTES.between(first, time)
        if (minutes % step != 0L) return null
        return (minutes / step).takeIf { count == null || it < count }
    }
}
