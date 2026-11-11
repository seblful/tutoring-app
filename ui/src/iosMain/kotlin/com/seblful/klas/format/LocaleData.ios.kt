package com.seblful.klas.format

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSFormattingContextMiddleOfSentence
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.NSMakeRange
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.characterDirectionForLanguage
import platform.Foundation.compare
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.languageCode
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages
import platform.Foundation.timeZoneForSecondsFromGMT
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

    /**
     * Month and weekday names as CLDR writes them, read by formatting real dates mid-sentence. Foundation's
     * symbol lists apply the language's capitalisation rules (Russian «Пн», «Январь»); `java.time` does
     * not, and the shared formatters capitalise where a heading needs it.
     */
    val raw = NSDateFormatter().apply {
        this.locale = locale
        calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
        timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        formattingContext = NSFormattingContextMiddleOfSentence
    }
    fun named(pattern: String, dates: List<NSDate>): List<String> {
        raw.dateFormat = pattern
        return dates.map { raw.stringFromDate(it) }
    }
    val week = DayOfWeek.entries.map { SAMPLE_MONDAY.plus(it.ordinal, DateTimeUnit.DAY).toNSDate() }
    val year = Month.entries.map { LocalDate(SAMPLE_MONDAY.year, it, MID_MONTH).toNSDate() }

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
        months = named("MMMM", year),
        shortMonths = named("MMM", year),
        standaloneMonths = named("LLLL", year),
        weekdays = named("EEEE", week),
        shortWeekdays = named("EEE", week),
        narrowWeekdays = named("EEEEE", week),
        standaloneWeekdays = named("cccc", week),
        standaloneShortWeekdays = named("ccc", week),
        standaloneNarrowWeekdays = named("ccccc", week),
        amPm = listOf(names.AMSymbol, names.PMSymbol),
        era = names.eraSymbols.strings().lastOrNull().orEmpty(),
        decimalSeparator = latin.decimalSeparator.firstOrNull() ?: '.',
        groupingSeparator = latin.groupingSeparator.firstOrNull() ?: ',',
        currencyPlacement = currencyPlacementOf(currency.positiveFormat),
        rightToLeft = NSLocale.characterDirectionForLanguage(locale.languageCode) ==
            NSLocaleLanguageDirectionRightToLeft,
    )
}

actual fun currencyDisplayName(code: String, tag: String): String? =
    nsLocale(tag).displayNameForKey(NSLocaleCurrencyCode, value = code)

@OptIn(ExperimentalForeignApi::class)
actual fun collatorFor(tag: String): Comparator<String> {
    val locale = nsLocale(tag)
    return Comparator { first, second ->
        val text = first.ns()
        text.compare(second, options = 0u, range = NSMakeRange(0u, text.length), locale = locale).toInt()
    }
}

actual fun titlecaseFirst(text: String, tag: String): String {
    if (text.isEmpty()) return text
    val first = text.substring(0, if (text[0].isHighSurrogate()) 2 else 1)
    return first.ns().uppercaseStringWithLocale(nsLocale(tag)) + text.substring(first.length)
}

/**
 * A real `NSString`: a Kotlin string cast to one reaches Foundation's category methods as the wrong
 * object, which on iOS crashed inside `uppercaseStringWithLocale`.
 */
@OptIn(BetaInteropApi::class)
private fun String.ns(): NSString = NSString.create(string = this)

internal actual fun platformDefaultTag(): String =
    NSLocale.preferredLanguages.firstOrNull() as? String ?: NSLocale.currentLocale.localeIdentifier

/** A Monday, from which the week's and the year's names are read. */
private val SAMPLE_MONDAY = LocalDate(2024, Month.JANUARY, 1)
private const val MID_MONTH = 15
private const val NOON = 12

/** Noon UTC on this day: no zone or daylight-saving change moves it to another date. */
private fun LocalDate.toNSDate(): NSDate {
    val noon = LocalDateTime(this, LocalTime(hour = NOON, minute = 0)).toInstant(TimeZone.UTC)
    return NSDate.dateWithTimeIntervalSince1970(noon.epochSeconds.toDouble())
}

private fun nsLocale(tag: String): NSLocale =
    NSLocale(localeIdentifier = NSLocale.canonicalLocaleIdentifierFromString(tag))
