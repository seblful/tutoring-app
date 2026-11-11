package com.seblful.klas.format

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSString
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages
import platform.Foundation.uppercaseStringWithLocale

/** Foundation's CLDR tables: the same patterns and names `java.text` and `java.time` read on Android. */
internal actual fun platformLocaleData(tag: String): LocaleData {
    val locale = nsLocale(tag)
    fun formatter(date: NSDateFormatterStyle, time: NSDateFormatterStyle) = NSDateFormatter().apply {
        this.locale = locale
        calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
        dateStyle = date
        timeStyle = time
    }
    val names = formatter(NSDateFormatterLongStyle, NSDateFormatterNoStyle)

    @Suppress("UNCHECKED_CAST")
    fun List<*>?.strings(): List<String> = (this as List<String>?).orEmpty()

    /** Foundation's weeks start on Sunday; the shared formatters' on Monday. */
    fun List<*>?.mondayFirst(): List<String> = strings().let { it.drop(1) + it.take(1) }

    val latin = NSNumberFormatter().apply {
        this.locale = nsLocale("$tag@numbers=latn")
        numberStyle = NSNumberFormatterDecimalStyle
    }
    val currency = NSNumberFormatter().apply {
        this.locale = locale
        numberStyle = NSNumberFormatterCurrencyStyle
    }
    return LocaleData(
        tag = tag,
        longDatePattern = names.dateFormat,
        fullDatePattern = formatter(NSDateFormatterFullStyle, NSDateFormatterNoStyle).dateFormat,
        shortTimePattern = formatter(NSDateFormatterNoStyle, NSDateFormatterShortStyle).dateFormat,
        months = names.monthSymbols.strings(),
        shortMonths = names.shortMonthSymbols.strings(),
        standaloneMonths = names.standaloneMonthSymbols.strings(),
        weekdays = names.weekdaySymbols.mondayFirst(),
        shortWeekdays = names.shortWeekdaySymbols.mondayFirst(),
        narrowWeekdays = names.veryShortWeekdaySymbols.mondayFirst(),
        standaloneWeekdays = names.standaloneWeekdaySymbols.mondayFirst(),
        standaloneShortWeekdays = names.shortStandaloneWeekdaySymbols.mondayFirst(),
        standaloneNarrowWeekdays = names.veryShortStandaloneWeekdaySymbols.mondayFirst(),
        amPm = listOf(names.AMSymbol, names.PMSymbol),
        era = names.eraSymbols.strings().lastOrNull().orEmpty(),
        decimalSeparator = latin.decimalSeparator.firstOrNull() ?: '.',
        groupingSeparator = latin.groupingSeparator.firstOrNull() ?: ',',
        currencyPlacement = currencyPlacementOf(currency.positiveFormat),
    )
}

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun titlecaseFirst(text: String, tag: String): String {
    if (text.isEmpty()) return text
    val first = text.substring(0, if (text[0].isHighSurrogate()) 2 else 1)
    return (first as NSString).uppercaseStringWithLocale(nsLocale(tag)) + text.substring(first.length)
}

internal actual fun platformDefaultTag(): String =
    NSLocale.preferredLanguages.firstOrNull() as? String ?: NSLocale.currentLocale.localeIdentifier

private fun nsLocale(tag: String): NSLocale =
    NSLocale(localeIdentifier = NSLocale.canonicalLocaleIdentifierFromString(tag))
