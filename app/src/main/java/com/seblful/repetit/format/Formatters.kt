package com.seblful.repetit.format

import com.seblful.repetit.domain.model.Money
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

/** Formats [Money] as a plain editable amount with no currency sign, e.g. "50", "45,05", "-0,50". */
fun Money.toAmountInput(): String {
    val sign = if (minor < 0) "-" else ""
    val abs = kotlin.math.abs(minor)
    val major = abs / 100
    val kopecks = (abs % 100).toInt()
    return if (kopecks == 0) "$sign$major" else "$sign$major,${kopecks.toString().padStart(2, '0')}"
}

/** Formats an epoch-month key (year * 12 + monthIndex) as e.g. "Июнь 2026". */
fun formatMonthKey(key: Int): String {
    val year = key / 12
    val month = key % 12 + 1
    return formatMonth(YearMonth.of(year, month))
}

/** Formats a month as e.g. "Июнь 2026" — the schedule calendar's page title. */
fun formatMonth(month: YearMonth): String =
    month.atDay(1).format(monthFmt).replaceFirstChar { it.uppercase() }

/** Short weekday name for a calendar header, e.g. "пн". */
fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT_STANDALONE, RU)
