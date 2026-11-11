package com.seblful.klas.format

import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Patterns and month names from `java.text`, whose format-form months are the genitive «октября»; weekday,
 * standalone month and marker names from `java.time`, as a `DateTimeFormatter` pattern would print them.
 */
internal actual fun platformLocaleData(tag: String): LocaleData {
    val locale = Locale.forLanguageTag(tag)
    val symbols = DateFormatSymbols.getInstance(locale)
    fun pattern(format: DateFormat): String = (format as? SimpleDateFormat)?.toPattern().orEmpty()
    fun months(style: TextStyle): List<String> = Month.entries.map { it.getDisplayName(style, locale) }
    fun weekdays(style: TextStyle): List<String> = DayOfWeek.entries.map { it.getDisplayName(style, locale) }
    fun text(pattern: String, value: java.time.temporal.TemporalAccessor): String =
        DateTimeFormatter.ofPattern(pattern, locale).format(value)
    val latin = DecimalFormatSymbols.getInstance(
        Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build(),
    )
    return LocaleData(
        tag = tag,
        longDatePattern = pattern(DateFormat.getDateInstance(DateFormat.LONG, locale)),
        fullDatePattern = pattern(DateFormat.getDateInstance(DateFormat.FULL, locale)),
        shortTimePattern = pattern(DateFormat.getTimeInstance(DateFormat.SHORT, locale)),
        months = symbols.months.take(MONTHS),
        shortMonths = symbols.shortMonths.take(MONTHS),
        standaloneMonths = months(TextStyle.FULL_STANDALONE),
        weekdays = weekdays(TextStyle.FULL),
        shortWeekdays = weekdays(TextStyle.SHORT),
        narrowWeekdays = weekdays(TextStyle.NARROW),
        standaloneWeekdays = weekdays(TextStyle.FULL_STANDALONE),
        standaloneShortWeekdays = weekdays(TextStyle.SHORT_STANDALONE),
        standaloneNarrowWeekdays = weekdays(TextStyle.NARROW_STANDALONE),
        amPm = listOf(text("a", LocalTime.of(MORNING, 0)), text("a", LocalTime.of(AFTERNOON, 0))),
        era = text("G", LocalDate.of(ERA_YEAR, 1, 1)),
        decimalSeparator = latin.decimalSeparator,
        groupingSeparator = latin.groupingSeparator,
        currencyPlacement = currencyPlacementOf(
            (NumberFormat.getCurrencyInstance(locale) as? DecimalFormat)?.toPattern().orEmpty(),
        ),
    )
}

internal actual fun titlecaseFirst(text: String, tag: String): String =
    text.replaceFirstChar { it.titlecase(Locale.forLanguageTag(tag)) }

internal actual fun platformDefaultTag(): String = Locale.getDefault().toLanguageTag()

private const val MONTHS = 12
private const val MORNING = 1
private const val AFTERNOON = 13
private const val ERA_YEAR = 2000
