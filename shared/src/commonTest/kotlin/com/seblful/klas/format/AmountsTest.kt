package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountsTest {
    @Test
    fun bothSeparatorsAndWhitespacePreserveMinorUnits() {
        listOf("1500.50", "1500,50", "1 500,50", "1\u00a0500,50", "1\u202f500.50").forEach {
            assertEquals(Money(150050), parseMoneyOrNull(it), it)
        }
        assertEquals(Money(50), parseMoneyOrNull(".5"))
        assertEquals(Money.ofMajor(5), parseMoneyOrNull("5."))
        assertEquals(Money(1), parseMoneyOrNull("0000.005"))
    }

    @Test
    fun roundingAndCeilingAreExact() {
        assertEquals(Money.ZERO, parseMoneyOrNull("0.004999999999999999"))
        assertEquals(Money(1), parseMoneyOrNull("0.005"))
        assertEquals(Money(101), parseMoneyOrNull("1.005"))
        assertEquals(Money.MAX, parseMoneyOrNull("1000000.0049999999999999"))
        assertNull(parseMoneyOrNull("1000000.005"))
        assertNull(parseMoneyOrNull("9".repeat(10000)))
        assertEquals(Money(1), parseMoneyOrNull("0".repeat(10000) + ".01"))
    }

    @Test
    fun onlyPlainNonNegativeDecimalAmountsAreAccepted() {
        listOf("", " ", ".", "-5", "+5", "NaN", "Infinity", "1e3", "1e2147483647", "1.2.3", "1,2.3", "１２").forEach {
            assertNull(parseMoneyOrNull(it), it)
        }
    }
}
