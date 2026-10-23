package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyParsingLimitsTest {
    @Test
    fun `localized keyboard digits and Arabic decimal separator read as minor units`() {
        for (text in listOf("25٫50", "٢٥٫٥٠", "۲۵٫۵۰", "२५.५०", "２５.５０")) {
            assertEquals(Money(2550), parseMoneyOrNull(text), text)
        }
        assertEquals(Money(1), parseMoneyOrNull("٠٫٠٠٥"))
        assertNull(parseMoneyOrNull("١٠٠٠٠٠٠٫٠٠٥"), "localized amounts still obey the input ceiling")
        assertNull(parseMoneyOrNull("٢٥٫٥٫٠"))
        assertNull(parseMoneyOrNull("١٬٥٠٠"), "a grouping mark is not a decimal separator")
        assertNull(parseMoneyOrNull("-٢٥"))
    }

    @Test
    fun `scientific notation is not an amount - however large or small`() {
        assertNull(parseMoneyOrNull("1e3"))
        assertNull(parseMoneyOrNull("1E3"))
        assertNull(parseMoneyOrNull("1e2147483647"))
        assertNull(parseMoneyOrNull("1e100000000"))
        assertNull(parseMoneyOrNull("1e-2147483647"))
    }

    @Test
    fun `the edges of a plain amount round as written`() {
        assertEquals(Money(1), parseMoneyOrNull("0.005"))
        assertEquals(Money.ZERO, parseMoneyOrNull("0.004"))
        assertEquals(Money.MAX, parseMoneyOrNull("1000000.004"))
        assertNull(parseMoneyOrNull("1000000.005"))
        assertNull(parseMoneyOrNull("9".repeat(10_000)), "an oversized amount is refused")
    }

    @Test
    fun `an amount is digits and at most one separator`() {
        assertEquals(Money(50), parseMoneyOrNull(".5"))
        assertEquals(Money.ofMajor(5), parseMoneyOrNull("5."))
        assertNull(parseMoneyOrNull("+5"))
        assertNull(parseMoneyOrNull("-5"))
        assertNull(parseMoneyOrNull("1.2.3"))
        assertNull(parseMoneyOrNull("."))
    }

    @Test
    fun `thousands grouped with no-break spaces still read`() {
        assertEquals(Money.ofMajor(1_500), parseMoneyOrNull("1" + Char(0x00A0) + "500"))
        assertEquals(Money.ofMajor(1_500), parseMoneyOrNull("1" + Char(0x202F) + "500"))
    }
}
