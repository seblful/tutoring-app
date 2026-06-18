package com.seblful.tutoringapp.ui.common

import com.seblful.tutoringapp.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU = Locale("ru")
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

/** Parses a user-entered amount in rubles (comma or dot decimals) into [Money], or null. */
fun parseMoneyOrNull(text: String): Money? {
    val normalized = text.trim().replace(',', '.').replace(" ", "")
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
    return Money(Math.round(value * 100))
}

/** Formats an epoch-month key (year * 12 + monthIndex) as e.g. "Июнь 2026". */
fun formatMonthKey(key: Int): String {
    val year = key / 12
    val month = key % 12 + 1
    return LocalDate.of(year, month, 1).format(monthFmt).replaceFirstChar { it.uppercase() }
}
