package com.seblful.repetit.format

import com.seblful.repetit.domain.model.Money
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Instant.toLocalDate(): LocalDate = atZone(zone).toLocalDate()
fun Instant.toLocalTime(): LocalTime = atZone(zone).toLocalTime()

fun Instant.formatDate(): String = atZone(zone).format(dateFmt)
fun Instant.formatDay(): String = atZone(zone).format(dayFmt).replaceFirstChar { it.uppercase() }
fun Instant.formatTime(): String = atZone(zone).format(timeFmt)
fun Instant.formatDateTime(): String = atZone(zone).format(dateTimeFmt)

/**
 * Parses a user-entered amount in rubles (comma or dot decimals) into [Money], or null.
 * Uses [BigDecimal] — not floating point — so it matches the integer-kopeck money model exactly,
 * rejects non-finite input ("Infinity"/"NaN") and clamps nothing: an amount too large to fit in
 * [Money]'s `Long` minor units is rejected (null) rather than silently overflowing.
 */
fun parseMoneyOrNull(text: String): Money? {
    val normalized = text.trim().replace(',', '.').replace(" ", "")
    if (normalized.isEmpty()) return null
    val value = normalized.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: return null
    val minor = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
    return runCatching { Money(minor.longValueExact()) }.getOrNull()
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
