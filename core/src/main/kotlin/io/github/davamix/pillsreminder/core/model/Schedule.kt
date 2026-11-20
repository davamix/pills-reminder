package io.github.davamix.pillsreminder.core.model

import java.time.LocalDateTime

/** The unit of an interval. Every unit is a fixed number of minutes on the phone's clock (spec §4.3 rule 6). */
enum class IntervalUnit(
    internal val minutes: Long,
) {
    HOURS(60),
    DAYS(1_440),
    WEEKS(10_080),

    /** Debug builds only (plan D-15). */
    MINUTES(1),
}

/** The time between two takes of a medicine (TR-3). */
data class Interval(
    val amount: Int,
    val unit: IntervalUnit,
) {
    init {
        require(amount >= 1) { "An interval needs an amount of at least 1, not $amount" }
    }

    /** The interval in minutes. It can't overflow: amounts are Ints. */
    val minutes: Long get() = amount * unit.minutes
}

/** How long a medicine is taken, counted from its first take (TR-3). A week is 7 days. */
sealed interface MedicineDuration {
    data class Days(
        val count: Int,
    ) : MedicineDuration {
        init {
            require(count >= 1) { "A duration needs at least 1 day, not $count" }
        }
    }

    data class Weeks(
        val count: Int,
    ) : MedicineDuration {
        init {
            require(count >= 1) { "A duration needs at least 1 week, not $count" }
        }
    }

    data object NoEnd : MedicineDuration
}

/** The duration in minutes, or `null` for no end. */
internal val MedicineDuration.minutes: Long?
    get() =
        when (this) {
            is MedicineDuration.Days -> count * IntervalUnit.DAYS.minutes
            is MedicineDuration.Weeks -> count * IntervalUnit.WEEKS.minutes
            MedicineDuration.NoEnd -> null
        }

/** A medicine's first take, interval and duration. Its first take has whole minutes (P2-D3). */
data class Schedule(
    val firstTake: LocalDateTime,
    val interval: Interval,
    val duration: MedicineDuration,
) {
    init {
        require(firstTake.second == 0 && firstTake.nano == 0) { "A first take has whole minutes, not $firstTake" }
    }
}

/**
 * An edited medicine's earlier schedule (TR-10). It applies to its own takes planned after the previous earlier
 * schedule's [until], up to and including its own [until].
 */
data class EarlierSchedule(
    val schedule: Schedule,
    val until: LocalDateTime,
)
