package io.github.davamix.pillsreminder.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.davamix.pillsreminder.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The words and patterns [DateTimeText] writes with, from `strings_common.xml` in the app. */
data class DateTimeWords(
    val today: String,
    val tomorrow: String,
    val yesterday: String,
    /** A day, then a time: `%1$s %2$s`. */
    val dayAndTime: String,
    /** A date in today's year, such as `EEE d MMM`. */
    val datePattern: String,
    /** A date in another year, such as `EEE d MMM yyyy`. */
    val dateWithYearPattern: String,
)

/**
 * Dates and times as text (P6-D4, spec NF-8). Day and month names are in [locale], the UI's language, which comes from
 * the resources with the words, so a translation brings its own; the hour format follows the phone's setting.
 *
 * A day is "Today", "Tomorrow", "Yesterday", or a date such as "Mon 23 Nov", with the year when it isn't today's.
 */
class DateTimeText(
    private val locale: Locale,
    is24Hour: Boolean,
    private val words: DateTimeWords,
) {
    private val timeFormat = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)
    private val dateFormat = DateTimeFormatter.ofPattern(words.datePattern, locale)
    private val dateWithYearFormat = DateTimeFormatter.ofPattern(words.dateWithYearPattern, locale)
    private val numberFormat = NumberFormat.getIntegerInstance(locale)

    /** The time of day: "16:00", or "4:00 PM" on a 12-hour phone. */
    fun time(at: LocalDateTime): String = timeFormat.format(at)

    /** The date, such as "Mon 23 Nov", with the year when it isn't [today]'s: "Mon 30 Nov 2027". */
    fun date(
        date: LocalDate,
        today: LocalDate,
    ): String = (if (date.year == today.year) dateFormat else dateWithYearFormat).format(date)

    /** "Today", "Tomorrow" or "Yesterday" next to [today], or else the [date]. */
    fun day(
        date: LocalDate,
        today: LocalDate,
    ): String =
        when (ChronoUnit.DAYS.between(today, date)) {
            0L -> words.today
            1L -> words.tomorrow
            -1L -> words.yesterday
            else -> date(date, today)
        }

    /** A day and a time, such as "Today 16:00" or "Mon 30 Nov 00:00". */
    fun dateTime(
        at: LocalDateTime,
        today: LocalDate,
    ): String = String.format(locale, words.dayAndTime, day(at.toLocalDate(), today), time(at))

    /** A count, with the locale's grouping: "1,000". */
    fun number(count: Long): String = numberFormat.format(count)
}

/**
 * The app's [DateTimeText]: the words, the patterns and their locale from the resources, and the phone's 24-hour
 * setting, read again at each composition. `stringResource` follows a change of the configuration, such as the language.
 */
@Composable
fun rememberDateTimeText(): DateTimeText {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val locale = stringResource(R.string.date_locale)
    val words =
        DateTimeWords(
            today = stringResource(R.string.day_today),
            tomorrow = stringResource(R.string.day_tomorrow),
            yesterday = stringResource(R.string.day_yesterday),
            dayAndTime = stringResource(R.string.day_and_time),
            datePattern = stringResource(R.string.date_pattern),
            dateWithYearPattern = stringResource(R.string.date_pattern_with_year),
        )
    return remember(locale, is24Hour, words) { DateTimeText(Locale.forLanguageTag(locale), is24Hour, words) }
}
