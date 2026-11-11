package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.MINUTES_PER_HOUR
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.Volatile
import kotlin.time.Instant

/**
 * The app's date and time formats in one language, ordered by the platform's own patterns: «14 октября»,
 * "October 14", «10月14日».
 *
 * The order comes from the language's LONG and FULL date patterns with the year taken out, rather than from
 * ICU skeletons: `android.icu` is unavailable in JVM tests, and the patterns read the same CLDR tables on
 * every platform. Month names in a date are the format forms, the genitive «октября» a Russian date needs.
 */
private class Formats(val data: LocaleData) {

    private val longDate = DatePattern.parse(data.longDatePattern)
    private val dayMonthPattern = longDate.withoutYear()

    val dayMonth: DatePattern = dayMonthPattern
    val dayShortMonth: DatePattern = dayMonthPattern.withShortMonth()

    /** With the year; a trailing era word («2026 г.») is dropped, as a date in a list reads without it. */
    val date: DatePattern = longDate.withShortMonth().withoutTrailingText()
    val day: DatePattern = DatePattern.parse(data.fullDatePattern).withoutYear()

    /** True when [dayMonth] reads day first, «12 июля», so a range can share its month: «6 – 12 июля». */
    val dayFirst: Boolean = dayMonthPattern.startsWithDay

    /** True when [dayMonth] ends on the day, "July 12", so a range can share its month: "July 6 – 12". */
    val dayLast: Boolean = dayMonthPattern.endsWithDay

    /**
     * The 12-hour face of a time, for a phone whose clock is set that way — asked for by `hour12`, never by
     * this module deciding. The app's own default is the 24-hour `HH:mm`.
     *
     * A language that writes a 12-hour clock natively uses its own pattern and marker: "3:20 PM", «오후 3:20»,
     * «下午3:20». The rest get a Latin "3:20 PM": `ru` renders its marker «ДП»/«ПП» under some CLDR
     * versions and "AM"/"PM" under others, and the Latin pair is what a 12-hour reader expects there.
     */
    private val nativeTime12: DatePattern? = DatePattern.parse(
        data.shortTimePattern
            // `B`, a flexible day period (newer CLDR's zh-TW), is written as the plain AM/PM marker.
            .replace(Regex("[Bb]+"), "a")
            .replace(NARROW_NO_BREAK_SPACE, ' '),
    ).takeIf { it.isTwelveHour }

    fun time(time: LocalTime, hour12: Boolean): String = when {
        !hour12 -> "${twoDigits(time.hour)}:${twoDigits(time.minute)}"
        nativeTime12 != null -> nativeTime12.format(data, time = time)
        else -> {
            val hour = (time.hour % HALF_DAY).takeIf { it != 0 } ?: HALF_DAY
            "$hour:${twoDigits(time.minute)} ${if (time.hour < HALF_DAY) "AM" else "PM"}"
        }
    }

    fun dateTime(at: LocalDateTime, hour12: Boolean): String =
        "${dayShortMonth.format(data, at.date)}, ${time(at.time, hour12)}"

    fun capitalized(text: String): String = titlecaseFirst(text, data.tag)

    private companion object {
        const val NARROW_NO_BREAK_SPACE = '\u202F'
        const val HALF_DAY = 12

        fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
    }
}

/** Copied on write: a race only builds a language's formats twice. */
@Volatile
private var cache: Map<String, Formats> = emptyMap()

private fun formatsFor(tag: String): Formats =
    cache[tag] ?: Formats(localeData(tag)).also { cache = cache + (tag to it) }

private val formats: Formats get() = formatsFor(AppLocale.tag)

private val zone: TimeZone get() = TimeZone.currentSystemDefault()

fun Instant.toLocalDate(): LocalDate = toLocalDateTime(zone).date
fun Instant.toLocalTime(): LocalTime = toLocalDateTime(zone).time

fun Instant.formatDate(): String = toLocalDate().formatDate()

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
fun Instant.formatTime(hour12: Boolean, tag: String = AppLocale.tag): String =
    formatsFor(tag).time(toLocalTime(), hour12)

fun Instant.formatDateTime(hour12: Boolean): String = formats.dateTime(toLocalDateTime(zone), hour12)

// The same formats for callers already holding a local date or time. Before these existed, five
// screens spelled `atStartOfDay(ZoneId.systemDefault()).toInstant()` to reach the Instant overloads
// — the zone convention this module owns, restated at every call — and the booking form grew its own
// "%02d:%02d" for lesson times, a second spelling of the 24-hour face.
fun LocalDate.formatDate(): String = formats.let { it.date.format(it.data, this) }
fun LocalDate.formatDay(): String = formats.let { it.capitalized(it.day.format(it.data, this)) }

/** "12 Sep", or "12 Sep 2025" when it falls outside [thisYear] — a date a list reads in passing. */
fun LocalDate.formatNearDate(thisYear: Int): String =
    formats.let { (if (year == thisYear) it.dayShortMonth else it.date).format(it.data, this) }
fun LocalTime.formatTime(hour12: Boolean): String = formats.time(this, hour12)

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
    formats.let { it.capitalized(it.data.standaloneMonths[monthIndex]) }

/** Formats a month as e.g. "Июнь 2026" — the schedule calendar's page title. */
fun formatMonth(month: YearMonth): String =
    formats.let { it.capitalized("${it.data.standaloneMonths[month.month.ordinal]} ${month.year}") }

/** Short weekday name for a calendar header, e.g. "пн" / "Mon". */
fun DayOfWeek.shortName(): String = formats.data.standaloneShortWeekdays[ordinal]

fun DayOfWeek.narrowName(): String = formats.data.standaloneNarrowWeekdays[ordinal]

/** The weekday's full name on its own, capitalised: "Sunday", «Воскресенье». */
fun DayOfWeek.fullDisplayName(): String = formats.let { it.capitalized(it.data.standaloneWeekdays[ordinal]) }

/** [shortName] starting a phrase: «Чт 16:00», not «чт 16:00». */
fun DayOfWeek.shortDisplayName(): String = formats.capitalized(shortName())

/**
 * Formats a week as an inclusive range, dropping whatever the two ends share so the label stays
 * short enough to sit between the navigator's arrows:
 * "6 – 12 июля", "July 6 – 12", "29 июня – 5 июля", "28 дек. 2026 – 3 янв. 2027". A language whose day
 * is wrapped in text («7月6日») repeats the month instead: «7月6日 – 7月12日».
 */
fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    val f = formats
    fun LocalDate.dayMonth() = f.dayMonth.format(f.data, this)
    return when {
        start.year != end.year -> "${f.date.format(f.data, start)} – ${f.date.format(f.data, end)}"
        start.month != end.month -> "${start.dayMonth()} – ${end.dayMonth()}"
        f.dayFirst -> "${start.day} – ${end.dayMonth()}"
        f.dayLast -> "${start.dayMonth()} – ${end.day}"
        else -> "${start.dayMonth()} – ${end.dayMonth()}"
    }
}
