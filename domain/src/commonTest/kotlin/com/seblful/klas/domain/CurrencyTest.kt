package com.seblful.klas.domain

import com.seblful.klas.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrencyTest {

    @Test
    fun `a code the app knows names its currency`() {
        assertEquals(Currency.BYN, Currency.forCode("BYN"))
        assertEquals(Currency.RUB, Currency.forCode("RUB"))
        assertEquals(Currency.USD, Currency.forCode("USD"))
        assertEquals(Currency.EUR, Currency.forCode("EUR"))
    }

    @Test
    fun `a code outside the four - or none - names nothing`() {
        assertNull(Currency.forCode("GBP"))
        assertNull(Currency.forCode("PLN"))
        assertNull(Currency.forCode(null), "a locale with no region has no currency")
    }

    @Test
    fun `the first region the app recognises wins - and dollars are the last resort`() {
        assertEquals(Currency.BYN, Currency.preferredFor(listOf("BYN", "RUB")))
        assertEquals(
            Currency.RUB,
            Currency.preferredFor(listOf(null, "RUB")),
            "a language-only locale is skipped rather than answered",
        )
        assertEquals(Currency.USD, Currency.preferredFor(listOf("GBP", "PLN")))
        assertEquals(Currency.USD, Currency.preferredFor(emptyList()), "nothing to ask at all")
        assertEquals(Currency.USD, Currency.FALLBACK)
    }

    @Test
    fun `an unrecognised stored currency reads as BYN`() {
        assertEquals(Currency.BYN, Currency.parse("BYN"))
        assertEquals(Currency.USD, Currency.parse("USD"))
        assertEquals(Currency.EUR, Currency.parse("EUR"))
        assertEquals(Currency.BYN, Currency.parse("GBP"))
        assertEquals(Currency.BYN, Currency.parse(""))
        assertEquals(Currency.BYN, Currency.parse("$"), "a symbol is not a name")
    }
}
