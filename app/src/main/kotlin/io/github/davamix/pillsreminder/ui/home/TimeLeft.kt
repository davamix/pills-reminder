package io.github.davamix.pillsreminder.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import java.time.Duration
import java.time.Instant

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * 60L

/**
 * The time from now to a planned instant, in whole minutes (HM-1, P7 SPEC §4.3.3): rounded up while it is ahead, so a
 * take a few seconds away is "in 1 min", and down once it has passed, so a take due under a minute ago is "now".
 * [past] is true from the planned instant on.
 */
data class TimeLeft(
    val past: Boolean,
    val days: Long,
    val hours: Int,
    val minutes: Int,
) {
    companion object {
        /** From [now] to [at], both instants, so a daylight saving change counts as the real time it adds or removes. */
        fun between(
            now: Instant,
            at: Instant,
        ): TimeLeft {
            val millis = Duration.between(now, at).toMillis()
            val past = millis <= 0
            val total = if (past) -millis / MILLIS_PER_MINUTE else (millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE
            return TimeLeft(
                past = past,
                days = total / MINUTES_PER_DAY,
                hours = ((total % MINUTES_PER_DAY) / MINUTES_PER_HOUR).toInt(),
                minutes = (total % MINUTES_PER_HOUR).toInt(),
            )
        }

        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

/** "in 5 h 30 min", "in 1 day 7 h", "5 min ago" or "now". From a day on, the minutes are dropped. */
@Composable
fun timeLeftText(time: TimeLeft): String {
    val amount =
        when {
            time.days > 0 -> {
                val days = pluralStringResource(R.plurals.home_days, time.days.toInt(), time.days.toInt())
                if (time.hours > 0) stringResource(R.string.home_days_hours, days, time.hours) else days
            }
            time.hours > 0 && time.minutes > 0 -> stringResource(R.string.home_hours_minutes, time.hours, time.minutes)
            time.hours > 0 -> stringResource(R.string.home_hours, time.hours)
            time.minutes > 0 -> stringResource(R.string.home_minutes, time.minutes)
            else -> return stringResource(R.string.home_time_now)
        }
    return stringResource(if (time.past) R.string.home_time_ago else R.string.home_time_in, amount)
}
