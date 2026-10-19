package com.seblful.klas.domain

import com.seblful.klas.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which currency a phone starts on, and what a stored word reads back as.
 *
 * The rule worth stating is that it comes off the **region** and not the language, because the case it gets
 * right is the app's own audience: a tutor in Minsk whose phone is in English is paid in rubles, and reading
 * the currency off "English" would hand them dollars.
 */
class CurrencyTest {

    @Test
    fun `a code the app knows names its currency`() {
        assertEquals(Currency.BYN, Currency.forCode("BYN"))
        assertEquals(Currency.RUB, Currency.forCode("RUB"))
        assertEquals(Currency.USD, Currency.forCode("USD"))
        assertEquals(Currency.EUR, Currency.forCode("EUR"))
    }

    @Test
    fun `a code outside the four, or none, names nothing`() {
        assertNull(Currency.forCode("GBP"))
        assertNull(Currency.forCode("PLN"))
        assertNull("a locale with no region has no currency", Currency.forCode(null))
    }

    /** The region's currency, not the language's: an English phone in Minsk is still paid in rubles. */
    @Test
    fun `the first region the app recognises wins, and dollars are the last resort`() {
        assertEquals(Currency.BYN, Currency.preferredFor(listOf("BYN", "RUB")))
        assertEquals(
            "a language-only locale is skipped rather than answered",
            Currency.RUB,
            Currency.preferredFor(listOf(null, "RUB")),
        )
        assertEquals(Currency.USD, Currency.preferredFor(listOf("GBP", "PLN")))
        assertEquals("nothing to ask at all", Currency.USD, Currency.preferredFor(emptyList()))
        assertEquals(Currency.USD, Currency.FALLBACK)
    }

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
