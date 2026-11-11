package com.seblful.klas.format

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.NSMakeRange
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSString
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.characterDirectionForLanguage
import platform.Foundation.compare
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
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

    /**
     * Stand-alone names as CLDR writes them. Foundation applies the language's capitalisation context to
     * them (Russian «Пн», «Январь»); `java.time` does not, and the shared formatters capitalise where a
     * heading needs it. A stand-alone name differing from the in-sentence one only by case is that.
     */
    fun List<String>.uncapitalisedLike(inSentence: List<String>): List<String> =
        zip(inSentence) { alone, within -> if (alone.equals(within, ignoreCase = true)) within else alone }

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
        standaloneMonths = names.standaloneMonthSymbols.strings().uncapitalisedLike(names.monthSymbols.strings()),
        weekdays = names.weekdaySymbols.mondayFirst(),
        shortWeekdays = names.shortWeekdaySymbols.mondayFirst(),
        narrowWeekdays = names.veryShortWeekdaySymbols.mondayFirst(),
        standaloneWeekdays = names.standaloneWeekdaySymbols.mondayFirst()
            .uncapitalisedLike(names.weekdaySymbols.mondayFirst()),
        standaloneShortWeekdays = names.shortStandaloneWeekdaySymbols.mondayFirst()
            .uncapitalisedLike(names.shortWeekdaySymbols.mondayFirst()),
        standaloneNarrowWeekdays = names.veryShortStandaloneWeekdaySymbols.mondayFirst()
            .uncapitalisedLike(names.veryShortWeekdaySymbols.mondayFirst()),
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

private fun nsLocale(tag: String): NSLocale =
    NSLocale(localeIdentifier = NSLocale.canonicalLocaleIdentifierFromString(tag))
