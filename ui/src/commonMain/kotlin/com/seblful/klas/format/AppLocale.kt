package com.seblful.klas.format

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import kotlin.concurrent.Volatile

/**
 * The foreground language as a BCP 47 tag, published by RootViewModel before its settings reach the UI.
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
    var tag: String = platformDefaultTag()

    /**
     * The decimal separator [tag] writes money with: `,` in Russian, `.` in English.
     *
     * The one place the app answers that question. `Money.amountText` takes it as a parameter with no
     * default precisely so this is the only source, and it is read from the platform's own tables rather
     * than from a `when` over two languages — a third translation should not have to remember to add itself
     * here.
     *
     * Arabic's own separators, «٬» and «٫», belong with Arabic-Indic digits: beside Latin ones they wrote
     * «16٬037٫50» and asked for a «٫» to be typed, so these are the Latin digits' separators.
     */
    val decimalSeparator: Char get() = localeData(tag).decimalSeparator

    /**
     * The thousands separator [tag] groups a displayed amount with: a no-break space in Russian (so
     * `16 037` never breaks across a line), `,` in English. From the platform's tables, like
     * [decimalSeparator].
     */
    val groupingSeparator: Char get() = localeData(tag).groupingSeparator
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
 * The amount as it is **shown**, captioned with [currency], in the language [tag] — `16 037,50 ₽`,
 * `$16,037.50`, `€ 1.500,50`: the separators and the sign's side are the language's. Every display of money
 * goes through this, so an amount cannot render two ways.
 */
fun Money.money(currency: Currency, tag: String = AppLocale.tag): String {
    val data = localeData(tag)
    return format(currency, data.decimalSeparator, data.groupingSeparator, data.currencyPlacement)
}

/**
 * System notification fonts cannot render the bundled Belarusian ruble glyph. Its code, being letters, takes
 * a space from digits it precedes: "BYN 1,500".
 */
fun String.forSystemFont(): String =
    replace(Regex(Regex.escape(Currency.BYN.symbol) + "(?=\\d)"), Currency.BYN.name + " ")
        .replace(Currency.BYN.symbol, Currency.BYN.name)
