package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import java.math.RoundingMode
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
 * Shared display formatting for dates, times and money in the app's `ru` locale. Lives in a
 * neutral module (not `ui`) so non-UI callers — notifications, workers, view models — use the
 * same formats without depending on the UI layer.
 */

private val RU = Locale.forLanguageTag("ru")
private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", RU)
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", RU)
private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", RU)
private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMMM", RU)
private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", RU)

/**
 * The 12-hour faces of [timeFmt] and [dateTimeFmt], for a phone whose clock is set that way — asked for
 * by `hour12`, never by this module deciding. The app's own default is the 24-hour one above.
 *
 * The marker is spelled "AM"/"PM" here rather than taken from the locale: `ru` renders it «ДП»/«ПП» under
 * some CLDR versions and "AM"/"PM" under others, so an unpinned marker would have read differently on two
 * Androids — and it is the Latin pair a tutor reading a 12-hour clock expects. Built rather than patterned
 * because a pattern's `a` is exactly the part that varies.
 */
private val amPm = mapOf(0L to "AM", 1L to "PM")
private val time12Fmt = DateTimeFormatterBuilder()
    .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .appendLiteral(' ')
    .appendText(ChronoField.AMPM_OF_DAY, amPm)
    .toFormatter(RU)
private val dateTime12Fmt = DateTimeFormatterBuilder()
    .appendPattern("d MMM, ")
    .append(time12Fmt)
    .toFormatter(RU)

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Instant.toLocalDate(): LocalDate = atZone(zone).toLocalDate()
fun Instant.toLocalTime(): LocalTime = atZone(zone).toLocalTime()

fun Instant.formatDate(): String = atZone(zone).format(dateFmt)
fun Instant.formatDay(): String = atZone(zone).format(dayFmt).replaceFirstChar { it.uppercase() }

// A clock is the one format the *phone* has an opinion about, so these take it as an argument. The
// default is the app's 24-hour face, which keeps every caller that has no phone to ask — a notification,
// a report, an export — printing what it always did. UI goes through `ui/common/timeText` instead of
// passing the flag itself.
fun Instant.formatTime(hour12: Boolean = false): String =
    atZone(zone).format(if (hour12) time12Fmt else timeFmt)

fun Instant.formatDateTime(hour12: Boolean = false): String =
    atZone(zone).format(if (hour12) dateTime12Fmt else dateTimeFmt)

// The same formats for callers already holding a local date or time. Before these existed, five
// screens spelled `atStartOfDay(ZoneId.systemDefault()).toInstant()` to reach the Instant overloads
// — the zone convention this module owns, restated at every call — and the booking form grew its own
// "%02d:%02d" for lesson times, a second spelling of [timeFmt].
fun LocalDate.formatDate(): String = format(dateFmt)
fun LocalDate.formatDay(): String = format(dayFmt).replaceFirstChar { it.uppercase() }
fun LocalTime.formatTime(hour12: Boolean = false): String = format(if (hour12) time12Fmt else timeFmt)

/**
 * Parses a user-entered amount in rubles (comma or dot decimals) into [Money], or null.
 * Uses [BigDecimal] — not floating point — so it matches the integer-kopeck money model exactly,
 * rejects non-finite input ("Infinity"/"NaN") and clamps nothing: an amount too large to fit in
 * [Money]'s `Long` minor units is rejected (null) rather than silently overflowing.
 *
 * **This is the app's one gate on how large an amount can be** — anything above [Money.MAX] is
 * rejected here, where the answer is still "that is not an amount" rather than a number that has
 * already lost its meaning. Fitting in a `Long` is not enough: the charge for a lesson is
 * `rate × minutes`, so a rate that fits can still bill a wrapped, negative amount. See [Money.MAX]
 * for why that particular ceiling.
 */
fun parseMoneyOrNull(text: String): Money? {
    val normalized = text.trim().replace(',', '.').replace(" ", "")
    if (normalized.isEmpty()) return null
    val value = normalized.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: return null
    val minor = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
    return runCatching { Money(minor.longValueExact()) }.getOrNull()?.takeIf { it <= Money.MAX }
}

/**
 * Formats [Money] as a plain editable amount with no currency sign, e.g. "50", "45,05", "-0,50".
 *
 * Kept as the name a text field seeds itself with, but the rendering itself is [Money.amountText] —
 * the same string [Money.format] puts a currency after — so the two can't drift.
 */
fun Money.toAmountInput(): String = amountText

/**
 * Formats a number of minutes as hours for a stat tile: `90` → "1,5", `2520` → "42", `30` → "0,5".
 *
 * Rounded to a tenth of an hour with integer arithmetic, matching the money model's refusal to let
 * floating point near a displayed number, and a whole number of hours prints with no decimal at all —
 * "42 ч" rather than "42,0 ч", since the tile is a summary and the extra digit only adds noise.
 */
fun formatHours(minutes: Int): String {
    val tenths = (minutes * 10 + 30) / 60
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "$whole" else "$whole,$fraction"
}

/** Formats an epoch-month key (year * 12 + monthIndex) as e.g. "Июнь 2026". */
fun formatMonthKey(key: Int): String {
    val year = key / 12
    val month = key % 12 + 1
    return formatMonth(YearMonth.of(year, month))
}

/**
 * The month of an epoch-month key on its own, e.g. "Июнь" — for a list that already sits under the
 * year it belongs to, where repeating "2026" on all twelve rows says nothing.
 */
fun formatMonthName(key: Int): String =
    Month.of(key % 12 + 1).getDisplayName(TextStyle.FULL_STANDALONE, RU)
        .replaceFirstChar { it.uppercase() }

/** Formats a month as e.g. "Июнь 2026" — the schedule calendar's page title. */
fun formatMonth(month: YearMonth): String =
    month.atDay(1).format(monthFmt).replaceFirstChar { it.uppercase() }

/** Short weekday name for a calendar header, e.g. "пн". */
fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT_STANDALONE, RU)

/**
 * Formats a week as an inclusive range, dropping whatever the two ends share so the label stays
 * short enough to sit between the navigator's arrows:
 * "6 – 12 июля", "29 июня – 5 июля", "28 дек. 2026 – 3 янв. 2027".
 */
fun formatWeekRange(start: LocalDate, end: LocalDate): String = when {
    start.year != end.year -> "${start.format(dateFmt)} – ${end.format(dateFmt)}"
    start.month != end.month -> "${start.format(dayMonthFmt)} – ${end.format(dayMonthFmt)}"
    else -> "${start.dayOfMonth} – ${end.format(dayMonthFmt)}"
}
