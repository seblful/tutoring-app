package com.seblful.klas.format

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.SymbolPlacement
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The foreground locale, published by RootViewModel before its settings reach the UI.
 * Background adapters resolve their own DisplayLocale from shared language state.
 * The device's default locale remains untouched.
 */
object AppLocale {

    /**
     * Where the app starts before a settings row has been read: the phone's own locale, unresolved.
     *
     * Deliberately *not* `AppLanguage.FALLBACK`. `AppLanguage.SYSTEM` is the default choice, so on the vast
     * majority of launches the phone's locale is the answer the row will confirm a few milliseconds later —
     * and where it is not, the month names are briefly in the phone's language rather than briefly in a
     * language nobody asked for.
     */
    @Volatile
    var current: Locale = Locale.getDefault()

    /**
     * The decimal separator [current] writes money with: `,` in Russian, `.` in English.
     *
     * The one place the app answers that question. `Money.amountText` takes it as a parameter with no
     * default precisely so this is the only source, and it is read from the platform's own tables rather
     * than from a `when` over two languages — a third translation should not have to remember to add itself
     * here.
     */
    val decimalSeparator: Char get() = DecimalFormatSymbols.getInstance(current).decimalSeparator

    /**
     * The thousands separator [current] groups a displayed amount with: a no-break space in Russian (so
     * `16 037` never breaks across a line), `,` in English. From the platform's tables, like
     * [decimalSeparator].
     */
    val groupingSeparator: Char get() = DecimalFormatSymbols.getInstance(current).groupingSeparator
}

/**
 * The amount as it is **typed**, in the app's language — `1500,50` or `1500.50`, never grouped: what a money
 * field is seeded with, and what `parseMoneyOrNull` reads back.
 *
 * The `format/` half of [Money.amountText]: the domain states how an amount is built and refuses to guess
 * the separator, and this supplies the app's answer to it.
 */
fun Money.amount(): String = amountText(AppLocale.decimalSeparator)

/**
 * The amount as it is **shown**, captioned with [currency], in the app's language — `16 037,50 ₽`,
 * `$16,037.50`, `€ 1.500,50`: the separators and the sign's side are the language's. Every display of money
 * goes through this, so an amount cannot render two ways.
 */
fun Money.money(currency: Currency, locale: Locale = AppLocale.current): String {
    val style = moneyStyles.getOrPut(locale) { MoneyStyle.of(locale) }
    return format(currency, style.decimal, style.grouping, style.placement)
}

/** How [locale] writes an amount of money, read from the platform's own tables. */
private data class MoneyStyle(val decimal: Char, val grouping: Char, val placement: SymbolPlacement) {
    companion object {
        fun of(locale: Locale): MoneyStyle {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            return MoneyStyle(symbols.decimalSeparator, symbols.groupingSeparator, placementIn(locale))
        }

        /** "¤#,##0.00" puts the sign first, "#,##0.00 ¤" last; a space beside a leading sign is kept. */
        fun placementIn(locale: Locale): SymbolPlacement {
            val pattern = (NumberFormat.getCurrencyInstance(locale) as? DecimalFormat)?.toPattern()
                ?.substringBefore(';') ?: return SymbolPlacement.AFTER
            val sign = pattern.indexOf(CURRENCY_SIGN)
            val digits = pattern.indexOfFirst { it == '#' || it == '0' }
            return when {
                sign < 0 || digits < 0 || sign > digits -> SymbolPlacement.AFTER
                pattern.substring(sign + 1, digits).any(Char::isWhitespace) -> SymbolPlacement.BEFORE_SPACED
                else -> SymbolPlacement.BEFORE
            }
        }

        private const val CURRENCY_SIGN = '\u00A4'
    }
}

/** Each locale's money style: every amount on a list asks, and `getInstance` copies. */
private val moneyStyles = ConcurrentHashMap<Locale, MoneyStyle>()

/**
 * System notification fonts cannot render the bundled Belarusian ruble glyph. Its code, being letters, takes
 * a space from digits it precedes: "BYN 1,500".
 */
fun String.forSystemFont(): String =
    replace(Regex(Regex.escape(Currency.BYN.symbol) + "(?=\\d)"), Currency.BYN.name + " ")
        .replace(Currency.BYN.symbol, Currency.BYN.name)
