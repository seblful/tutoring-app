package com.seblful.klas.format

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The locale the app is currently **displaying** in — the resolved answer to
 * `AppSettings.language`, published once so that the words and the numbers cannot disagree.
 *
 * Ambient, and for the same reason `Formatters`' zone is: at the moment anything is rendered there is
 * exactly one answer. Which language the app speaks is a single fact about a single tutor's single
 * settings row, and threading a `Locale` through seventeen display functions, `MoneyField`, `DaySummary`
 * and every one of their call sites would be ceremony with no decision behind it. It is *not* the JVM
 * default: `Locale.getDefault` is the **phone's** and is read as such by `WeekStart`, which asks it what
 * the tutor set their week to — overwriting it would answer that question with the app's own preference.
 *
 * **Published wherever the settings row is read or written** (`SettingsRepository.observe`/`get`/`update`),
 * which is what makes it impossible for a consumer to be handed a language and format a date in the previous
 * one: every reader of the row — the UI, the workers, the notifier — goes through there, so the locale is
 * already current by the time the new `AppSettings` arrives. Seeded from the phone, so a process that formats
 * something before any row has been read still formats it in a language the strings exist in.
 *
 * A JVM test sets [current] directly; nothing else may write it.
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
}

/**
 * The amount with no currency, in the app's language — `1500,50` or `1500.50`.
 *
 * The `format/` half of [Money.amountText]: the domain states how an amount is built and refuses to guess
 * the separator, and this supplies the app's answer to it. Every display of money goes through one of these
 * two functions, so an amount cannot render two ways.
 */
fun Money.amount(): String = amountText(AppLocale.decimalSeparator)

/** The amount captioned with [currency], in the app's language — `1500,50 BYN`, `1500.50 USD`. */
fun Money.money(currency: Currency): String = format(currency, AppLocale.decimalSeparator)
