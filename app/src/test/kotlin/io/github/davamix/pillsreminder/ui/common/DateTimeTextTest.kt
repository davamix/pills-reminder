package io.github.davamix.pillsreminder.ui.common

import io.github.davamix.pillsreminder.data.nov
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Dates and times as text (P6-D4, spec NF-8), with the English words of `strings_common.xml`. */
class DateTimeTextTest {
    private val words =
        DateTimeWords(
            today = "Today",
            tomorrow = "Tomorrow",
            yesterday = "Yesterday",
            dayAndTime = "%1\$s %2\$s",
            datePattern = "EEE d MMM",
            dateWithYearPattern = "EEE d MMM yyyy",
        )
    private val english = Locale.forLanguageTag("en")
    private val text24 = DateTimeText(english, is24Hour = true, words)
    private val text12 = DateTimeText(english, is24Hour = false, words)

    /** Sunday 22 November 2026, the day before the §4.4 example starts. */
    private val today: LocalDate = nov(22, 0).toLocalDate()

    @Test
    fun `a 24-hour phone writes times from 00h00 to 23h59 with two digits`() {
        assertEquals("16:00", text24.time(nov(23, 16)))
        assertEquals("08:05", text24.time(nov(23, 8, 5)))
        assertEquals("00:00", text24.time(nov(24, 0)))
    }

    @Test
    fun `a 12-hour phone writes times with AM and PM, and midnight as 12`() {
        assertEquals("4:00 PM", text12.time(nov(23, 16)))
        assertEquals("8:05 AM", text12.time(nov(23, 8, 5)))
        assertEquals("12:00 AM", text12.time(nov(24, 0)))
    }

    @Test
    fun `the days next to today have their names, and the others their dates`() {
        assertEquals("Today", text24.day(nov(22, 0).toLocalDate(), today))
        assertEquals("Tomorrow", text24.day(nov(23, 0).toLocalDate(), today))
        assertEquals("Yesterday", text24.day(nov(21, 0).toLocalDate(), today))
        assertEquals("Tue 24 Nov", text24.day(nov(24, 0).toLocalDate(), today))
        assertEquals("Fri 20 Nov", text24.day(nov(20, 0).toLocalDate(), today))
    }

    @Test
    fun `a date in another year shows its year, before and after today's`() {
        assertEquals("Mon 30 Nov", text24.date(nov(30, 0).toLocalDate(), today))
        assertEquals("Tue 30 Nov 2027", text24.date(LocalDate.of(2027, 11, 30), today))
        assertEquals("Wed 31 Dec 2025", text24.date(LocalDate.of(2025, 12, 31), today))
    }

    @Test
    fun `a planned time is its day and its time`() {
        assertEquals("Today 16:00", text24.dateTime(nov(22, 16), today))
        assertEquals("Tomorrow 08:00", text24.dateTime(nov(23, 8), today))
        assertEquals("Mon 30 Nov 00:00", text24.dateTime(nov(30, 0), today))
        assertEquals("Mon 30 Nov 12:00 AM", text12.dateTime(nov(30, 0), today))
    }

    @Test
    fun `a date across a year's end is a day apart`() {
        val newYearsEve = LocalDate.of(2026, 12, 31)
        assertEquals("Tomorrow", text24.day(LocalDate.of(2027, 1, 1), newYearsEve))
        assertEquals("Fri 1 Jan 2027 08:00", text24.dateTime(LocalDateTime.of(2027, 1, 1, 8, 0), LocalDate.of(2026, 12, 30)))
    }

    @Test
    fun `day and month names follow the locale the resources give, not the phone's`() {
        val spanish = DateTimeText(Locale.forLanguageTag("es"), is24Hour = true, words)
        assertEquals("lun 30 nov", spanish.date(nov(30, 0).toLocalDate(), today))
    }

    @Test
    fun `counts are grouped as the locale groups them`() {
        assertEquals("21", text24.number(21))
        assertEquals("1,000", text24.number(1_000))
        assertEquals("22,075,000,000,000", text24.number(22_075_000_000_000))
    }
}
