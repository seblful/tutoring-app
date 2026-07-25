package com.seblful.repetit.format

import com.seblful.repetit.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two money-input rules. The point of these tests is the *difference* between them: a rate may
 * be zero, a payment may not. Everything else they share, because they share a parser.
 */
class MoneyInputTest {

    @Test
    fun `a rate of zero is valid — teaching somebody for free is still a booked subject`() {
        assertEquals(Money.ZERO, MoneyInput.rate("0"))
        assertEquals(Money.ZERO, MoneyInput.rate("0,00"))
    }

    @Test
    fun `a payment of zero is not a payment`() {
        assertNull(MoneyInput.payment("0"))
        assertNull(MoneyInput.payment("0,00"))
    }

    @Test
    fun `zero is the only text the two rules disagree about`() {
        val texts = listOf("", "  ", "25", "25,50", "1 000", "-5", "abc", "NaN", "Infinity")
        texts.forEach { text ->
            assertEquals("disagreed about \"$text\"", MoneyInput.rate(text), MoneyInput.payment(text))
        }
    }

    @Test
    fun `both accept a plain amount, with either decimal separator`() {
        assertEquals(Money(2550), MoneyInput.rate("25,50"))
        assertEquals(Money(2550), MoneyInput.rate("25.50"))
        assertEquals(Money(2550), MoneyInput.payment("25,50"))
        assertEquals(Money(2550), MoneyInput.payment("25.50"))
    }

    @Test
    fun `both reject blank input`() {
        assertNull(MoneyInput.rate(""))
        assertNull(MoneyInput.rate("   "))
        assertNull(MoneyInput.payment(""))
        assertNull(MoneyInput.payment("   "))
    }

    @Test
    fun `both reject negative amounts — money owed is a balance, not an entry`() {
        assertNull(MoneyInput.rate("-5"))
        assertNull(MoneyInput.payment("-5"))
    }

    @Test
    fun `both reject text that is not an amount`() {
        listOf("abc", "1,2,3", "NaN", "Infinity", "1e9999").forEach {
            assertNull("rate accepted \"$it\"", MoneyInput.rate(it))
            assertNull("payment accepted \"$it\"", MoneyInput.payment(it))
        }
    }
}
