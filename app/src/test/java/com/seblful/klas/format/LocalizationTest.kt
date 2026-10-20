package com.seblful.klas.format

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocalizationTest {

    private fun locales(vararg tags: String) = tags.map(Locale::forLanguageTag)

    @Test
    fun `a region names its own currency, the eurozone included`() {
        assertEquals(
            listOf("BYN", "RUB", "USD", "EUR", "EUR"),
            locales("ru-BY", "ru-RU", "en-US", "de-DE", "en-IE").regionCurrencies(),
        )
    }

    @Test
    fun `a locale with no region, or one nobody knows, names no currency and does not throw`() {
        assertEquals(listOf(null, null, null), (locales("ru", "zz-ZZ") + Locale.ROOT).regionCurrencies())
    }

    @Test
    fun `an English phone in Minsk is still paid in rubles`() {
        assertEquals(Currency.BYN, Currency.preferredFor(locales("en-BY").regionCurrencies()))
        assertEquals(Currency.GBP, Currency.preferredFor(locales("en-GB", "pl-PL").regionCurrencies()))
        assertEquals(Currency.JPY, Currency.preferredFor(locales("en-JP").regionCurrencies()))
    }

    @Test
    fun `the resolved language is the locale the resources and formatters are given`() {
        assertEquals(Locale.forLanguageTag("ru"), AppLanguage.SYSTEM.localeFor(locales("ru-BY")))
        assertEquals(Locale.forLanguageTag("en"), AppLanguage.SYSTEM.localeFor(locales("fr-FR")))
        assertEquals(Locale.forLanguageTag("ru"), AppLanguage.RUSSIAN.localeFor(locales("en-US")))
    }
}
