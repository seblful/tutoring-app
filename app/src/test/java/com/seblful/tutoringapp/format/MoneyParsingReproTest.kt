package com.seblful.tutoringapp.format

import com.seblful.tutoringapp.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the money parse/format fixes (BUG-4: non-finite/overflowing input;
 * BUG-5: lost sign in toAmountInput). These started as failing repros and now pin the fix.
 */
class MoneyParsingReproTest {

    // ---- BUG-4: parseMoneyOrNull must reject non-finite / overflowing doubles ----

    @Test fun `parseMoneyOrNull rejects Infinity`() = assertNull(parseMoneyOrNull("Infinity"))

    @Test fun `parseMoneyOrNull rejects NaN`() = assertNull(parseMoneyOrNull("NaN"))

    @Test fun `parseMoneyOrNull rejects absurdly large scientific notation`() =
        assertNull(parseMoneyOrNull("1e20")) // overflows Long minor units

    @Test fun `parseMoneyOrNull rejects negative amounts`() = assertNull(parseMoneyOrNull("-5"))

    @Test fun `parseMoneyOrNull rejects blanks and junk`() {
        assertNull(parseMoneyOrNull(""))
        assertNull(parseMoneyOrNull("   "))
        assertNull(parseMoneyOrNull("abc"))
    }

    @Test fun `parseMoneyOrNull parses two decimals exactly`() {
        assertEquals(Money(4505), parseMoneyOrNull("45,05"))
        assertEquals(Money(5), parseMoneyOrNull("0,05"))
        assertEquals(Money(150000), parseMoneyOrNull("1500"))
    }

    @Test fun `parseMoneyOrNull rounds beyond two decimals to the nearest kopeck`() {
        assertEquals(Money(200), parseMoneyOrNull("1,999")) // 1.999 -> 2,00
        assertEquals(Money(124), parseMoneyOrNull("1,235")) // HALF_UP
    }

    @Test fun `parseMoneyOrNull accepts a plain zero`() = assertEquals(Money(0), parseMoneyOrNull("0"))

    // ---- BUG-5: Money.toAmountInput() must keep the sign for amounts in (-1, 0) ----

    @Test fun `toAmountInput keeps the sign for sub-ruble negative amounts`() {
        assertEquals("-0,50", Money(-50).toAmountInput())
        assertEquals("-0,05", Money(-5).toAmountInput())
    }

    @Test fun `toAmountInput formats positive and whole amounts unchanged`() {
        assertEquals("45,05", Money(4505).toAmountInput())
        assertEquals("50", Money(5000).toAmountInput())
        assertEquals("-1,50", Money(-150).toAmountInput())
    }
}
