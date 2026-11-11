package com.seblful.klas.format

import com.seblful.klas.domain.model.SymbolPlacement
import kotlin.concurrent.Volatile

/**
 * What one language's dates and amounts are built from, read from the platform's own CLDR tables —
 * `java.text`/`java.time` on Android, Foundation on iOS — so no language is listed in code and the shared
 * formatters only arrange the pieces.
 *
 * Patterns are CLDR (`java.text`) date patterns. Month lists run January first; weekday lists Monday first.
 */
class LocaleData(
    val tag: String,
    /** The long date: «d MMMM y 'г'.», "MMMM d, y", «y年M月d日». */
    val longDatePattern: String,
    /** The full date, with its weekday: «EEEE, d MMMM y 'г'.». */
    val fullDatePattern: String,
    /** The short time: "h:mm a", «HH:mm». Read only for a 12-hour clock the language writes natively. */
    val shortTimePattern: String,
    /** Month names as a date uses them — the genitive «октября». */
    val months: List<String>,
    val shortMonths: List<String>,
    /** Month names on their own — «Октябрь». */
    val standaloneMonths: List<String>,
    val weekdays: List<String>,
    val shortWeekdays: List<String>,
    val narrowWeekdays: List<String>,
    val standaloneWeekdays: List<String>,
    val standaloneShortWeekdays: List<String>,
    val standaloneNarrowWeekdays: List<String>,
    /** The AM and PM markers, in that order. */
    val amPm: List<String>,
    /** The current era's short name, "AD". */
    val era: String,
    /** The separators for the Latin digits every amount is written in (see [AppLocale.decimalSeparator]). */
    val decimalSeparator: Char,
    val groupingSeparator: Char,
    /** Which side of an amount the currency sign goes, from the language's currency pattern. */
    val currencyPlacement: SymbolPlacement,
    /** Whether the language is written right to left: Arabic, Hebrew, Persian. */
    val rightToLeft: Boolean,
)

/** [tag]'s tables, read once per language: every formatted date and amount asks. */
internal fun localeData(tag: String): LocaleData =
    localeDataCache[tag] ?: platformLocaleData(tag).also { localeDataCache = localeDataCache + (tag to it) }

/** Copied on write: a race only reads a language's tables twice. */
@Volatile
private var localeDataCache: Map<String, LocaleData> = emptyMap()

/** The platform's tables for [tag], a BCP 47 tag. */
internal expect fun platformLocaleData(tag: String): LocaleData

/** [text] with its first letter in title case as [tag] writes it: Turkish «i» becomes «İ». */
expect fun titlecaseFirst(text: String, tag: String): String

/** [code]'s name in the language [tag], «Белорусский рубль»; null where the platform has none. */
expect fun currencyDisplayName(code: String, tag: String): String?

/** Orders words the way the language [tag] alphabetizes them: «Ё» beside «Е», "é" beside "e". */
expect fun collatorFor(tag: String): Comparator<String>

/** The phone's own locale as a BCP 47 tag, before the app has read which language the tutor chose. */
internal expect fun platformDefaultTag(): String

/**
 * The side of the amount a currency pattern puts its sign: "¤#,##0.00" first, "#,##0.00 ¤" last; a space
 * beside a leading sign is kept.
 */
internal fun currencyPlacementOf(pattern: String): SymbolPlacement {
    val positive = pattern.substringBefore(';')
    val sign = positive.indexOf(CURRENCY_SIGN)
    val digits = positive.indexOfFirst { it == '#' || it == '0' }
    return when {
        sign < 0 || digits < 0 || sign > digits -> SymbolPlacement.AFTER
        positive.substring(sign + 1, digits).any(Char::isWhitespace) -> SymbolPlacement.BEFORE_SPACED
        else -> SymbolPlacement.BEFORE
    }
}

private const val CURRENCY_SIGN = '¤'
