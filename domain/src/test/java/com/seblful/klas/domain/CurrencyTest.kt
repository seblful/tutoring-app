package com.seblful.klas.domain

import com.seblful.klas.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Which currency a phone starts on, and what a stored word reads back as.
 *
 * The rule worth stating is that it comes off the **region** and not the language, because the case it gets
 * right is the app's own audience: a tutor in Minsk whose phone is in English is paid in rubles, and reading
 * the currency off "English" would hand them dollars.
 */
class CurrencyTest {

    private fun locales(vararg tags: String) = tags.map(Locale::forLanguageTag)

    @Test
    fun `a region the app knows names its own currency`() {
        assertEquals(Currency.BYN, Currency.forLocale(Locale.forLanguageTag("ru-BY")))
        assertEquals(Currency.RUB, Currency.forLocale(Locale.forLanguageTag("ru-RU")))
        assertEquals(Currency.USD, Currency.forLocale(Locale.forLanguageTag("en-US")))
        assertEquals("the eurozone comes from the platform's tables", Currency.EUR, Currency.forLocale(Locale.forLanguageTag("de-DE")))
        assertEquals(Currency.EUR, Currency.forLocale(Locale.forLanguageTag("en-IE")))
    }

    /** The language says nothing about the money. This is the whole point of reading the region. */
    @Test
    fun `an English phone in Minsk is still paid in rubles`() {
        assertEquals(Currency.BYN, Currency.preferredFor(locales("en-BY")))
        assertEquals(Currency.BYN, Currency.preferredFor(locales("en-BY", "ru-BY")))
    }

    /** A region with a currency the app does not offer is a missing answer, not a wrong one. */
    @Test
    fun `a region outside the four names nothing`() {
        assertNull(Currency.forLocale(Locale.forLanguageTag("en-GB")))
        assertNull(Currency.forLocale(Locale.forLanguageTag("pl-PL")))
    }

    /**
     * A locale with no country, or one the platform has never heard of, makes `java.util.Currency` throw.
     * That has to be a missing answer rather than a crash: this reads whatever a phone happens to hold, on
     * the path that decides what a fresh install's money is labelled with.
     */
    @Test
    fun `a locale with no region does not throw`() {
        assertNull(Currency.forLocale(Locale.forLanguageTag("ru")))
        assertNull(Currency.forLocale(Locale.ROOT))
        assertNull(Currency.forLocale(Locale.forLanguageTag("zz-ZZ")))
    }

    @Test
    fun `the first region the app recognises wins, and dollars are the last resort`() {
        assertEquals(
            "a language-only locale is skipped rather than answered",
            Currency.RUB,
            Currency.preferredFor(locales("ru", "ru-RU")),
        )
        assertEquals(Currency.USD, Currency.preferredFor(locales("en-GB", "pl-PL")))
        assertEquals("nothing to ask at all", Currency.USD, Currency.preferredFor(emptyList()))
        assertEquals(Currency.USD, Currency.FALLBACK)
    }

    /**
     * Total, and falling back to BYN — the app's first currency — rather than to [Currency.FALLBACK].
     * Deliberately different from [Currency.preferredFor]'s answer: this is a *stored* word coming back
     * unreadable, where the honest reading is "the value this build cannot parse was written by a build that
     * could", and BYN is what such a row most likely held.
     */
    @Test
    fun `an unrecognised stored currency reads as BYN`() {
        assertEquals(Currency.BYN, Currency.parse("BYN"))
        assertEquals(Currency.USD, Currency.parse("USD"))
        assertEquals(Currency.EUR, Currency.parse("EUR"))
        assertEquals(Currency.BYN, Currency.parse("GBP"))
        assertEquals(Currency.BYN, Currency.parse(""))
        assertEquals("a symbol is not a name", Currency.BYN, Currency.parse("$"))
    }
}
