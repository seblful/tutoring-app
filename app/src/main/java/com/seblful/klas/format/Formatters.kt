package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import java.math.RoundingMode
import java.text.DateFormatSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Shared display formatting for dates, times and money in the language the app is set to. Lives in a
 * neutral module (not `ui`) so non-UI callers — notifications, workers, view models — use the
 * same formats without depending on the UI layer.
 *
 * **The locale is [AppLocale.current], read per call.** These used to be module-level `val`s built against
 * a pinned `ru`, which was right while `values/` was the only string folder there was: a formatter built
 * once at class-load cannot follow a setting. It is one lookup and one `Map` read per format now, against a
 * cache keyed by locale — a `DateTimeFormatter` is immutable and thread-safe, so the sets are still built
 * once each, just once *per language* and on demand rather than once per process.
 */

/**
 * Every formatter this file needs, for one locale. Grouped rather than kept as nine parallel maps so that
 * "the formats for a language" is a single thing to build, look up and reason about — and so a tenth format
 * cannot be added to eight of the nine languages.
 */
private class Formats(val locale: Locale) {
    /**
     * A month as it reads after a day number — «24 сентября», «5 мая» — in full and short, taken from
     * `java.text` rather than asked of a `MMMM`/`MMM` pattern.
     *
     * On a phone `java.time` is core-library desugaring's, which fills the *format* month names with the
     * *stand-alone* ones, so a pattern printed «24 сентябрь» and «5 май» there while the JDK behind the unit
     * tests printed the right thing. `DateFormatSymbols` is the platform's own and is not desugared, and on
     * the JDK it agrees with `java.time` month for month. Pinned on a device by `MonthNameCaseTest`.
     * `LLLL` — a month on its own, as [month] prints it — is the one the desugared tables do have right.
     */
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

    /** January to December by month number, from a `java.text` array (which carries a thirteenth, empty). */
    private fun monthNames(names: Array<String>): Map<Long, String> =
        (1..12).associate { it.toLong() to names[it - 1] }
}

/**
 * The formats for [AppLocale.current], built on first use of each language and kept.
 *
 * A plain synchronized map rather than a `ConcurrentHashMap`: there are two languages, this is read on
 * whichever thread is drawing, and building a duplicate set would be harmless anyway — the entries are
 * immutable and interchangeable.
 */
private val cache = java.util.Collections.synchronizedMap(mutableMapOf<Locale, Formats>())

private val formats: Formats get() = cache.getOrPut(AppLocale.current) { Formats(AppLocale.current) }

/**
 * The zone an instant is **rendered** in: the phone's, read per call so a device that changes zone
 * mid-session prints the new one.
 *
 * Deliberately ambient, and deliberately *unlike* [hour12], which has no default at all. The distinction is
 * whether there is a second possible answer. Which clock face to use is the phone owner's preference and the
 * app must not guess it, so no surface may pick one by saying nothing. "Where is this phone" has exactly one
 * answer at render time, and threading it through seventeen display functions would be ceremony with no
 * decision behind it. The app's **language** is ambient for the same reason and by the same argument — see
 * [AppLocale], which is where that one is written down.
 *
 * It is also not the ambient read that mattered. `ScheduleViewModel.moveTo` and `ReportsViewModel` both used
 * to read `ZoneId.systemDefault()` themselves, and both are **write** paths — deciding which instant to
 * store, deciding which period the money is counted in — where the shipped path has to be the tested one
 * and a fixture has to be able to pin it. Those read the injected `Clock`'s zone now. This is a projection
 * of an instant onto a screen, and there is nothing here for a test to pin that a `Clock` would help with.
 */
private val zone: ZoneId get() = ZoneId.systemDefault()

fun Instant.toLocalDate(): LocalDate = atZone(zone).toLocalDate()
fun Instant.toLocalTime(): LocalTime = atZone(zone).toLocalTime()

fun Instant.formatDate(): String = atZone(zone).format(formats.date)
fun Instant.formatDay(): String = atZone(zone).format(formats.day).capitalizedForDisplay()

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
    atZone(zone).format(if (hour12) formats.time12 else formats.time)

fun Instant.formatDateTime(hour12: Boolean): String =
    atZone(zone).format(if (hour12) formats.dateTime12 else formats.dateTime)

// The same formats for callers already holding a local date or time. Before these existed, five
// screens spelled `atStartOfDay(ZoneId.systemDefault()).toInstant()` to reach the Instant overloads
// — the zone convention this module owns, restated at every call — and the booking form grew its own
// "%02d:%02d" for lesson times, a second spelling of the 24-hour face.
fun LocalDate.formatDate(): String = format(formats.date)
fun LocalDate.formatDay(): String = format(formats.day).capitalizedForDisplay()
fun LocalTime.formatTime(hour12: Boolean): String = format(if (hour12) formats.time12 else formats.time)

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

/** An amount as a person writes one: digits, optionally a separator and more digits — `500`, `45.5`, `.5`. */
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
    Month.of(key % 12 + 1).getDisplayName(TextStyle.FULL_STANDALONE, formats.locale)
        .capitalizedForDisplay()

/** Formats a month as e.g. "Июнь 2026" — the schedule calendar's page title. */
fun formatMonth(month: YearMonth): String =
    month.atDay(1).format(formats.month).capitalizedForDisplay()

/** Short weekday name for a calendar header, e.g. "пн" / "Mon". */
fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT_STANDALONE, formats.locale)

/**
 * Formats a week as an inclusive range, dropping whatever the two ends share so the label stays
 * short enough to sit between the navigator's arrows:
 * "6 – 12 июля", "29 июня – 5 июля", "28 дек. 2026 – 3 янв. 2027".
 */
fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    val f = formats
    return when {
        start.year != end.year -> "${start.format(f.date)} – ${end.format(f.date)}"
        start.month != end.month -> "${start.format(f.dayMonth)} – ${end.format(f.dayMonth)}"
        else -> "${start.dayOfMonth} – ${end.format(f.dayMonth)}"
    }
}

/**
 * A display string with its first letter raised — for the places a name is used as a **heading**.
 *
 * CLDR gives Russian its month and weekday names in lower case («июнь», «понедельник»), because that is how
 * the language writes them mid-sentence; English capitalises them already. Upper-casing the first character
 * is therefore a no-op in English and the correction in Russian, which is why it is applied unconditionally
 * rather than under a `when` over the language. Cased in the **formatting locale**, not the JVM default, so
 * the app's own answer is what decides — the difference is real (Turkish's dotless i), even if neither of
 * the app's two languages is where it bites.
 */
private fun String.capitalizedForDisplay(): String =
    replaceFirstChar { it.titlecase(formats.locale) }
