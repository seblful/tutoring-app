package com.seblful.klas.format

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.time.MINUTES_PER_HOUR
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts

/**
 * Formats [DaySummary.parts] in [res]'s words and [tag]'s numbers. Returns null when the summary has no
 * parts. Both are passed explicitly so screens and notifications use the language each one shows.
 *
 * @param currency the display currency.
 */
fun daySummaryText(
    res: Texts,
    summary: DaySummary,
    currency: Currency,
    tag: String = AppLocale.tag,
): String? {
    val parts = summary.parts
    if (parts.isEmpty()) return null
    return bulletJoin(parts.map { it.text(res, currency, tag) })
}

private fun DaySummary.Part.text(res: Texts, currency: Currency, tag: String): String = when (this) {
    is DaySummary.Part.Lessons -> res.plural(Plurals.lessons_count, count, count)
    is DaySummary.Part.Hours -> res.string(
        Strings.hours_value,
        formatHours(minutes, localeData(tag).decimalSeparator),
    )
    is DaySummary.Part.Earned -> money.money(currency, tag)
    is DaySummary.Part.Cancelled -> res.string(Strings.schedule_summary_cancelled, count)
}

/** A stretch of time as it is said: «55 min», «2 h», «4 h 15 min». */
fun hoursAndMinutesText(res: Texts, minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    val minutesText = res.string(Strings.minutes_short, rest)
    if (hours == 0) return minutesText
    val hoursText = res.plural(Plurals.hours_short, hours, hours)
    return if (rest == 0) hoursText else res.string(Strings.hours_and_minutes, hoursText, minutesText)
}
