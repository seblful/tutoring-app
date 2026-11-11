package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.MINUTES_PER_HOUR
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
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * The app's date and time formats in [locale], ordered by the platform's own patterns: «14 октября»,
 * "October 14", «10月14日».
 *
 * The order comes from `java.text.DateFormat`'s LONG and FULL patterns with the year taken out, rather than
 * from ICU skeletons: `android.icu` is unavailable in JVM tests, and `java.text` reads the same CLDR tables
 * on both. Month names still come from [DateFormatSymbols], whose format-form months are the genitive
 * «октября» a Russian date needs.
 */
private class Formats(val locale: Locale) {

    private val symbols = DateFormatSymbols.getInstance(locale)
    private val monthOfDay = monthNames(symbols.months)
    private val shortMonthOfDay = monthNames(symbols.shortMonths)

    private val longDate = DatePattern.parse(datePattern(DateFormat.LONG))
    private val dayMonthPattern = longDate.withoutYear()

    val dayMonth: DateTimeFormatter = formatter(dayMonthPattern)
    val dayShortMonth: DateTimeFormatter = formatter(dayMonthPattern.withShortMonth())

    /** With the year; a trailing era word («2026 г.») is dropped, as a date in a list reads without it. */
    val date: DateTimeFormatter = formatter(longDate.withShortMonth().withoutTrailingText())
    val day: DateTimeFormatter = formatter(DatePattern.parse(datePattern(DateFormat.FULL)).withoutYear())
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    val dateTime: DateTimeFormatter = build { append(dayShortMonth).appendLiteral(", ").append(time) }
    val month: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    /** True when [dayMonth] reads day first, «12 июля», so a range can share its month: «6 – 12 июля». */
    val dayFirst: Boolean = dayMonthPattern.startsWithDay

    /** True when [dayMonth] ends on the day, "July 12", so a range can share its month: "July 6 – 12". */
    val dayLast: Boolean = dayMonthPattern.endsWithDay

    /**
     * The 12-hour faces of [time] and [dateTime], for a phone whose clock is set that way — asked for by
     * `hour12`, never by this module deciding. The app's own default is the 24-hour one above.
     *
     * A language that writes a 12-hour clock natively uses its own pattern and marker: "3:20 PM", «오후 3:20»,
     * «下午3:20». The rest get a Latin "3:20 PM": `ru` renders its marker «ДП»/«ПП» under some CLDR
     * versions and "AM"/"PM" under others, and the Latin pair is what a 12-hour reader expects there.
     */
    val time12: DateTimeFormatter = nativeTime12() ?: DateTimeFormatterBuilder()
        .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendLiteral(' ')
        .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
        .toFormatter(locale)

    val dateTime12: DateTimeFormatter = build { append(dayShortMonth).appendLiteral(", ").append(time12) }

    private fun datePattern(style: Int): String =
        (DateFormat.getDateInstance(style, locale) as SimpleDateFormat).toPattern()

    private fun nativeTime12(): DateTimeFormatter? {
        val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT, locale) as SimpleDateFormat).toPattern()
            // `B` (flexible day period, newer CLDR's zh-TW) is unknown to Android's java.time.
            .replace(Regex("[Bb]+"), "a")
            .replace(NARROW_NO_BREAK_SPACE, ' ')
        val twelveHour = 'a' in pattern && ('h' in pattern || 'K' in pattern)
        return if (twelveHour) DateTimeFormatter.ofPattern(pattern, locale) else null
    }

    private fun formatter(pattern: DatePattern): DateTimeFormatter = build {
        for (piece in pattern.pieces) {
            when {
                piece is DatePattern.Piece.Text -> appendLiteral(piece.text)
                piece !is DatePattern.Piece.Field -> Unit
                piece.isShortMonth -> appendText(ChronoField.MONTH_OF_YEAR, shortMonthOfDay)
                piece.isFullMonth -> appendText(ChronoField.MONTH_OF_YEAR, monthOfDay)
                else -> appendPattern(piece.letter.toString().repeat(piece.width))
            }
        }
    }

    private fun build(parts: DateTimeFormatterBuilder.() -> Unit): DateTimeFormatter =
        DateTimeFormatterBuilder().apply(parts).toFormatter(locale)

    private fun monthNames(names: Array<String>): Map<Long, String> =
        (1..12).associate { it.toLong() to names[it - 1] }

    private companion object {
        const val NARROW_NO_BREAK_SPACE = ' '
    }
}

/** A `java.text` date pattern as fields ("MMMM", "d") and the literal text between them. */
internal class DatePattern(val pieces: List<Piece>) {

    sealed interface Piece {
        data class Field(val letter: Char, val width: Int) : Piece {
            private val isMonth: Boolean get() = letter in "ML"
            val isShortMonth: Boolean get() = isMonth && width == SHORT_TEXT
            val isFullMonth: Boolean get() = isMonth && width > SHORT_TEXT
        }
        data class Text(val text: String) : Piece
    }

    private val fields: List<Int> = pieces.indices.filter { pieces[it] is Piece.Field }

    val startsWithDay: Boolean get() = pieces.firstOrNull().isDay()
    val endsWithDay: Boolean get() = pieces.lastOrNull().isDay()

    /**
     * The pattern with its year and the text tying it in removed, wherever the year sits: "MMMM d, y" →
     * "MMMM d", «y年M月d日» → «M月d日», "d MMMM y EEEE" → "d MMMM EEEE".
     */
    fun withoutYear(): DatePattern {
        val year = fields.firstOrNull { (pieces[it] as Piece.Field).letter == 'y' } ?: return this
        val before = fields.lastOrNull { it < year }
        val after = fields.firstOrNull { it > year }
        return DatePattern(
            when {
                before == null -> pieces.drop(after ?: pieces.size)
                after == null -> pieces.take(before + 1)
                else -> pieces.take(before + 1) + pieces.drop(year + 1)
            },
        )
    }

    /** A full month name shortened, "MMMM" → "MMM"; a numeric month («M月») stays numeric. */
    fun withShortMonth(): DatePattern = DatePattern(
        pieces.map { if (it is Piece.Field && it.isFullMonth) it.copy(width = SHORT_TEXT) else it },
    )

    /** Drops text after a final year, «d MMMM y 'г'.» → «d MMMM y»; text after any other field stays («日»). */
    fun withoutTrailingText(): DatePattern {
        val last = fields.lastOrNull() ?: return this
        return if ((pieces[last] as Piece.Field).letter == 'y') DatePattern(pieces.take(last + 1)) else this
    }

    private fun Piece?.isDay(): Boolean = this is Piece.Field && letter == 'd'

    companion object {
        /** A text field's short width, "MMM"; wider is the full name, narrower a number. */
        const val SHORT_TEXT = 3

        fun parse(pattern: String): DatePattern {
            val pieces = mutableListOf<Piece>()
            val text = StringBuilder()
            fun flushText() {
                if (text.isNotEmpty()) pieces += Piece.Text(text.toString())
                text.clear()
            }
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                when {
                    c == '\'' -> {
                        val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                        if (end == i + 1) text.append('\'') else text.append(pattern, i + 1, end)
                        i = end + 1
                    }
                    c in 'a'..'z' || c in 'A'..'Z' -> {
                        flushText()
                        var j = i
                        while (j < pattern.length && pattern[j] == c) j++
                        pieces += Piece.Field(c, j - i)
                        i = j
                    }
                    else -> {
                        text.append(c)
                        i++
                    }
                }
            }
            flushText()
            return DatePattern(pieces)
        }
    }
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
    val tenths = (minutes * 10 + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR
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
 * The month at [monthIndex] (0–11) on its own, e.g. "Июнь" — for a list that already sits under the
 * year it belongs to, where repeating "2026" on all twelve rows says nothing.
 */
fun formatMonthName(monthIndex: Int): String =
    java.time.Month.of(monthIndex + 1).getDisplayName(TextStyle.FULL_STANDALONE, formats.locale)
        .capitalizedForDisplay()

/** Formats a month as e.g. "Июнь 2026" — the schedule calendar's page title. */
fun formatMonth(month: YearMonth): String =
    month.firstDay.toJavaLocalDate().format(formats.month).capitalizedForDisplay()

/** Short weekday name for a calendar header, e.g. "пн" / "Mon". */
fun DayOfWeek.shortName(): String = toJavaDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, formats.locale)

fun DayOfWeek.narrowName(): String = toJavaDayOfWeek().getDisplayName(TextStyle.NARROW_STANDALONE, formats.locale)

/** The weekday's full name on its own, capitalised: "Sunday", «Воскресенье». */
fun DayOfWeek.fullDisplayName(): String =
    toJavaDayOfWeek().getDisplayName(TextStyle.FULL_STANDALONE, formats.locale).capitalizedForDisplay()

/** [shortName] starting a phrase: «Чт 16:00», not «чт 16:00». */
fun DayOfWeek.shortDisplayName(): String = shortName().capitalizedForDisplay()

/**
 * Formats a week as an inclusive range, dropping whatever the two ends share so the label stays
 * short enough to sit between the navigator's arrows:
 * "6 – 12 июля", "July 6 – 12", "29 июня – 5 июля", "28 дек. 2026 – 3 янв. 2027". A language whose day
 * is wrapped in text («7月6日») repeats the month instead: «7月6日 – 7月12日».
 */
fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    val f = formats
    fun LocalDate.dayMonth() = toJavaLocalDate().format(f.dayMonth)
    return when {
        start.year != end.year -> "${start.toJavaLocalDate().format(f.date)} – ${end.toJavaLocalDate().format(f.date)}"
        start.month != end.month -> "${start.dayMonth()} – ${end.dayMonth()}"
        f.dayFirst -> "${start.day} – ${end.dayMonth()}"
        f.dayLast -> "${start.dayMonth()} – ${end.day}"
        else -> "${start.dayMonth()} – ${end.dayMonth()}"
    }
}

private fun String.capitalizedForDisplay(): String =
    replaceFirstChar { it.titlecase(formats.locale) }
