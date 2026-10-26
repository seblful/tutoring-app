package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toLocalDateTime
import java.text.DateFormatSymbols
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private class Formats(val locale: Locale) {

    private val symbols = DateFormatSymbols.getInstance(locale)
    private val monthOfDay = monthNames(symbols.months)
    private val shortMonthOfDay = monthNames(symbols.shortMonths)

    val dayMonth: DateTimeFormatter = build { appendPattern("d ").appendText(ChronoField.MONTH_OF_YEAR, monthOfDay) }
    val dayShortMonth: DateTimeFormatter =
        build { appendPattern("d ").appendText(ChronoField.MONTH_OF_YEAR, shortMonthOfDay) }

    val date: DateTimeFormatter = build { append(dayShortMonth).appendPattern(" yyyy") }
    val day: DateTimeFormatter = build { appendPattern("EEEE, ").append(dayMonth) }
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    val dateTime: DateTimeFormatter = build { append(dayShortMonth).appendPattern(", HH:mm") }
    val month: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    /**
     * The 12-hour faces of [time] and [dateTime], for a phone whose clock is set that way — asked for by
     * `hour12`, never by this module deciding. The app's own default is the 24-hour one above.
     *
     * The marker is spelled "AM"/"PM" here rather than taken from the locale: `ru` renders it «ДП»/«ПП»
     * under some CLDR versions and "AM"/"PM" under others, so an unpinned marker would have read
     * differently on two Androids — and it is the Latin pair a tutor reading a 12-hour clock expects, in
     * either language. Built rather than patterned because a pattern's `a` is exactly the part that varies.
     */
    val time12: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendLiteral(' ')
        .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
        .toFormatter(locale)

    val dateTime12: DateTimeFormatter = build { append(dayShortMonth).appendLiteral(", ").append(time12) }

    private fun build(parts: DateTimeFormatterBuilder.() -> Unit): DateTimeFormatter =
        DateTimeFormatterBuilder().apply(parts).toFormatter(locale)

    private fun monthNames(names: Array<String>): Map<Long, String> =
        (1..12).associate { it.toLong() to names[it - 1] }
}

private val cache = java.util.Collections.synchronizedMap(mutableMapOf<Locale, Formats>())

private fun formatsFor(locale: Locale): Formats = cache.getOrPut(locale) { Formats(locale) }

private val formats: Formats get() = formatsFor(AppLocale.current)

private val zone: TimeZone get() = TimeZone.currentSystemDefault()

fun Instant.toLocalDate(): LocalDate = toLocalDateTime(zone).date
fun Instant.toLocalTime(): LocalTime = toLocalDateTime(zone).time

private fun Instant.javaZoned(): ZonedDateTime = toJavaInstant().atZone(zone.toJavaZoneId())

fun Instant.formatDate(): String = javaZoned().format(formats.date)

/**
 * A clock is the one format the *phone* has an opinion about, so these take it as an argument — and
 * **deliberately have no default**, because a default is how the wrong clock got picked twice: screens
 * printed 21:00 for a lesson set at 9:00 PM, and the reminder for it did the same.
 *
 * The line is whether the text is read **on the phone** or away from it. A screen goes through
 * `ui/common/timeText`, which asks; anything else shown on the phone asks the same way
 * (`notifications/AndroidNotifier`). A report, an export or a backup passes `false`: it is read
 * somewhere else, so it keeps `HH:mm` whatever this phone is set to.
 */
fun Instant.formatTime(hour12: Boolean, locale: Locale = AppLocale.current): String =
    formatsFor(locale).let { javaZoned().format(if (hour12) it.time12 else it.time) }

fun Instant.formatDateTime(hour12: Boolean): String =
    javaZoned().format(if (hour12) formats.dateTime12 else formats.dateTime)

// The same formats for callers already holding a local date or time. Before these existed, five
// screens spelled `atStartOfDay(ZoneId.systemDefault()).toInstant()` to reach the Instant overloads
// — the zone convention this module owns, restated at every call — and the booking form grew its own
// "%02d:%02d" for lesson times, a second spelling of the 24-hour face.
fun LocalDate.formatDate(): String = toJavaLocalDate().format(formats.date)
fun LocalDate.formatDay(): String = toJavaLocalDate().format(formats.day).capitalizedForDisplay()

/** "12 Sep", or "12 Sep 2025" when it falls outside [thisYear] — a date a list reads in passing. */
fun LocalDate.formatNearDate(thisYear: Int): String =
    toJavaLocalDate().format(if (year == thisYear) formats.dayShortMonth else formats.date)
fun LocalTime.formatTime(hour12: Boolean): String =
    toJavaLocalTime().format(if (hour12) formats.time12 else formats.time)

/**
 * Formats [Money] as a plain editable amount with no currency sign, e.g. "50", "45,05", "-0,50".
 *
 * Kept as the name a text field seeds itself with, but the rendering itself is [Money.amount] — the same
 * string [money] puts a currency after — so the two can't drift.
 */
fun Money.toAmountInput(): String = amount()

/**
 * Formats a number of minutes as hours: `90` → "1,5", `2520` → "42", `30` → "0,5". The bare number —
 * a stat tile carries its own «Часов» label, and the schedule's day summary wraps it in `hours_value`.
 *
 * Rounded to a tenth of an hour with integer arithmetic, matching the money model's refusal to let
 * floating point near a displayed number, and a whole number of hours prints with no decimal at all —
 * "42 ч" rather than "42,0 ч", since both callers are summaries and the extra digit only adds noise.
 * The separator is the app language's, the same one money is written with.
 */
fun formatHours(minutes: Int, decimalSeparator: Char = AppLocale.decimalSeparator): String {
    val tenths = (minutes * 10 + 30) / 60
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "$whole" else "$whole$decimalSeparator$fraction"
}

/**
 * The app's one way of putting facts about a lesson on one line: «21:00 · Математика · Иван Петров».
 *
 * Blank parts are dropped rather than leaving a stranded separator, which is what makes it safe to hand it
 * everything that *might* be known — a lesson with no group name, a day with nothing called off.
 *
 * Shared by the notification adapter and the schedule's day summary, so the separator cannot differ between
 * the screen and the tray entry summarising it. Language plays no part — the bullet is the same in both —
 * so this is a plain function and not one of the locale-aware formats above.
 */
fun bulletJoin(parts: List<String>): String = parts.filter { it.isNotBlank() }.joinToString(BULLET)

/** [bulletJoin] for a caller that has its parts as arguments rather than as a list. */
fun bulletJoin(vararg parts: String): String = bulletJoin(parts.asList())

private const val BULLET = " · "

/**
 * The month of an epoch-month key on its own, e.g. "Июнь" — for a list that already sits under the
 * year it belongs to, where repeating "2026" on all twelve rows says nothing.
 */
fun formatMonthName(key: Int): String =
    java.time.Month.of(key % 12 + 1).getDisplayName(TextStyle.FULL_STANDALONE, formats.locale)
        .capitalizedForDisplay()

/** Formats a month as e.g. "Июнь 2026" — the schedule calendar's page title. */
fun formatMonth(month: YearMonth): String =
    month.firstDay.toJavaLocalDate().format(formats.month).capitalizedForDisplay()

/** Short weekday name for a calendar header, e.g. "пн" / "Mon". */
fun DayOfWeek.shortName(): String = toJavaDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, formats.locale)

fun DayOfWeek.narrowName(): String = toJavaDayOfWeek().getDisplayName(TextStyle.NARROW_STANDALONE, formats.locale)

/** [shortName] starting a phrase: «Чт 16:00», not «чт 16:00». */
fun DayOfWeek.shortDisplayName(): String = shortName().capitalizedForDisplay()

/**
 * Formats a week as an inclusive range, dropping whatever the two ends share so the label stays
 * short enough to sit between the navigator's arrows:
 * "6 – 12 июля", "29 июня – 5 июля", "28 дек. 2026 – 3 янв. 2027".
 */
fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    val f = formats
    return when {
        start.year != end.year -> "${start.toJavaLocalDate().format(f.date)} – ${end.toJavaLocalDate().format(f.date)}"
        start.month != end.month ->
            "${start.toJavaLocalDate().format(f.dayMonth)} – ${end.toJavaLocalDate().format(f.dayMonth)}"
        else -> "${start.day} – ${end.toJavaLocalDate().format(f.dayMonth)}"
    }
}

private fun String.capitalizedForDisplay(): String =
    replaceFirstChar { it.titlecase(formats.locale) }
