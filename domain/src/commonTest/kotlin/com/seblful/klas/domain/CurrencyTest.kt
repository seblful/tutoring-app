package com.seblful.klas.domain

import com.seblful.klas.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CurrencyTest {

    @Test
    fun `a code the app knows names its currency`() {
        Currency.entries.forEach { currency ->
            assertEquals(currency, Currency.forCode(currency.name))
            assertEquals(currency, Currency.parse(currency.name))
        }
    }

    @Test
    fun `an unsupported code or none names nothing`() {
        assertNull(Currency.forCode("XYZ"))
        assertNull(Currency.forCode("BTC"))
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
        assertEquals(Currency.GBP, Currency.preferredFor(listOf("GBP", "PLN")))
        assertEquals(Currency.JPY, Currency.preferredFor(listOf(null, "JPY", "USD")))
        assertEquals(Currency.USD, Currency.preferredFor(listOf("XYZ", null)))
        assertEquals(Currency.USD, Currency.preferredFor(emptyList()), "nothing to ask at all")
        assertEquals(Currency.USD, Currency.FALLBACK)
    }

    @Test
    fun `an unrecognised stored currency reads as BYN`() {
        assertEquals(Currency.BYN, Currency.parse("BYN"))
        assertEquals(Currency.USD, Currency.parse("USD"))
        assertEquals(Currency.EUR, Currency.parse("EUR"))
        assertEquals(Currency.BYN, Currency.parse("XYZ"))
        assertEquals(Currency.BYN, Currency.parse(""))
        assertEquals(Currency.BYN, Currency.parse("$"), "a symbol is not a name")
    }

    @Test
    fun `the stored fallback is the app's oldest currency rather than the region fallback`() {
        assertEquals(Currency.BYN, Currency.LEGACY)
        assertEquals(Currency.LEGACY, Currency.parse("XYZ"))
        assertNotEquals(Currency.LEGACY, Currency.FALLBACK)
    }

    @Test
    fun `no two currencies share a symbol`() {
        val shared = Currency.entries.groupBy { it.symbol }.filterValues { it.size > 1 }
        assertEquals(emptyMap(), shared, "a shared symbol says whose money it is to nobody")
    }

    @Test
    fun `a tutor anywhere is offered their own currency`() {
        assertEquals(Currency.VND, Currency.preferredFor(listOf("VND")))
        assertEquals(Currency.IDR, Currency.preferredFor(listOf(null, "IDR")))
        assertEquals(Currency.XOF, Currency.preferredFor(listOf("XOF")))
    }
}
