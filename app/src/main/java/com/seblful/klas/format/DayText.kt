package com.seblful.klas.format

import android.content.res.Resources
import com.seblful.klas.R
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.schedule.DaySummary
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats [DaySummary.parts] with localized Android resources. Returns null when the summary has no parts.
 * [Resources] is passed explicitly so screens and notifications use the app's selected language.
 *
 * @param currency the display currency.
 */
fun daySummaryText(
    res: Resources,
    summary: DaySummary,
    currency: Currency,
    locale: Locale = AppLocale.current,
): String? {
    val parts = summary.parts
    if (parts.isEmpty()) return null
    return bulletJoin(parts.map { it.text(res, currency, locale) })
}

private fun DaySummary.Part.text(res: Resources, currency: Currency, locale: Locale): String = when (this) {
    is DaySummary.Part.Lessons -> res.getQuantityString(R.plurals.lessons_count, count, count)
    is DaySummary.Part.Hours -> res.getString(
        R.string.hours_value,
        formatHours(minutes, DecimalFormatSymbols.getInstance(locale).decimalSeparator),
    )
    is DaySummary.Part.Earned -> money.money(currency, locale)
    is DaySummary.Part.Cancelled -> res.getString(R.string.schedule_summary_cancelled, count)
}

/** A stretch of time as it is said: «55 min», «2 h», «4 h 15 min». */
fun hoursAndMinutesText(res: Resources, minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    val minutesText = res.getString(R.string.minutes_short, rest)
    if (hours == 0) return minutesText
    val hoursText = res.getQuantityString(R.plurals.hours_short, hours, hours)
    return if (rest == 0) hoursText else res.getString(R.string.hours_and_minutes, hoursText, minutesText)
}

private const val MINUTES_PER_HOUR = 60
