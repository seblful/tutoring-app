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
import java.math.RoundingMode
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
    private val dayShortMonth: DateTimeFormatter =
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

private val formats: Formats get() = cache.getOrPut(AppLocale.current) { Formats(AppLocale.current) }

private val zone: TimeZone get() = TimeZone.currentSystemDefault()

fun Instant.toLocalDate(): LocalDate = toLocalDateTime(zone).date
fun Instant.toLocalTime(): LocalTime = toLocalDateTime(zone).time

private fun Instant.javaZoned(): ZonedDateTime = toJavaInstant().atZone(zone.toJavaZoneId())

fun Instant.formatDate(): String = javaZoned().format(formats.date)
fun Instant.formatDay(): String = javaZoned().format(formats.day).capitalizedForDisplay()

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
fun Instant.formatTime(hour12: Boolean): String =
    javaZoned().format(if (hour12) formats.time12 else formats.time)

fun Instant.formatDateTime(hour12: Boolean): String =
    javaZoned().format(if (hour12) formats.dateTime12 else formats.dateTime)

// The same formats for callers already holding a local date or time. Before these existed, five
// screens spelled `atStartOfDay(ZoneId.systemDefault()).toInstant()` to reach the Instant overloads
// — the zone convention this module owns, restated at every call — and the booking form grew its own
// "%02d:%02d" for lesson times, a second spelling of the 24-hour face.
fun LocalDate.formatDate(): String = toJavaLocalDate().format(formats.date)
fun LocalDate.formatDay(): String = toJavaLocalDate().format(formats.day).capitalizedForDisplay()
fun LocalTime.formatTime(hour12: Boolean): String =
    toJavaLocalTime().format(if (hour12) formats.time12 else formats.time)

/**
 * Parses a user-entered amount (comma or dot decimals) into [Money], or null.
 * Uses [java.math.BigDecimal] — not floating point — so it matches the integer-kopeck money model exactly,
 * rejects non-finite input ("Infinity"/"NaN") and clamps nothing: an amount too large to fit in
 * [Money]'s `Long` minor units is rejected (null) rather than silently overflowing.
 *
 * **Both separators, in both languages.** The app writes `1500,50` in Russian and `1500.50` in English
 * ([AppLocale.decimalSeparator]), and this accepts either whichever is being written — a field seeded before
 * the language changed still reads, and a tutor whose phone keyboard offers the other one is not refused.
 *
 * **This is the app's one gate on how large an amount can be** — anything above [Money.MAX] is
 * rejected here, where the answer is still "that is not an amount" rather than a number that has
 * already lost its meaning. Fitting in a `Long` is not enough: the charge for a lesson is
 * `rate × minutes`, so a rate that fits can still bill a wrapped, negative amount. See [Money.MAX]
 * for why that particular ceiling.
 *
 * **Digits and one separator, nothing else.** `BigDecimal` also reads scientific notation, so a pasted
 * `1e3` was a thousand and `1e2147483647` an exponent to expand; nobody writes a price that way, and
 * an amount the tutor did not type as digits is not one they meant. Spaces — plain and the no-break
 * ones Russian groups thousands with — are dropped, so a pasted `1 500` still reads.
 */
fun parseMoneyOrNull(text: String): Money? {
    val normalized = text.trim().replace(',', '.').filterNot { it.isWhitespace() }
    if (!PLAIN_AMOUNT.matches(normalized)) return null
    val value = normalized.toBigDecimalOrNull() ?: return null
    // Compare before scaling, so a pasted run of digits is refused without being multiplied out. Keep a
    // rounding margin above the ceiling, and return zero directly below half a kopeck.
    val unroundedLimit = java.math.BigDecimal.valueOf(Money.MAX.minor, 2).add(java.math.BigDecimal.ONE)
    if (value > unroundedLimit) return null
    if (value < java.math.BigDecimal("0.005")) return Money.ZERO
    return try {
        val minor = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        Money(minor.longValueExact()).takeIf { it <= Money.MAX }
    } catch (_: ArithmeticException) {
        null
    }
}

private val PLAIN_AMOUNT = Regex("""\d+(\.\d*)?|\.\d+""")

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
fun formatHours(minutes: Int): String {
    val tenths = (minutes * 10 + 30) / 60
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "$whole" else "$whole${AppLocale.decimalSeparator}$fraction"
}

/**
 * The app's one way of putting facts about a lesson on one line: «21:00 · Математика · Иван Петров».
 *
 * Blank parts are dropped rather than leaving a stranded separator, which is what makes it safe to hand it
 * everything that *might* be known — a lesson with no group name, a day with nothing called off.
 *
 * Here rather than in the notification adapter that used to own it (`AndroidNotifier.joined`, applied at
 * four sites): the schedule's day summary joins its clauses the same way, so a separator chosen in two
 * places is a separator that can differ between the screen and the tray entry summarising it. Language
 * plays no part — the bullet is the same in both — so this is a plain function and not one of the
 * locale-aware formats above.
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
