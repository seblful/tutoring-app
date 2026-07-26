package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The money-field rules, in one place. Each of these used to be restated at every text field that
 * needed it — three of them inside composables, where no JVM test could reach them at all.
 */
class MoneyFieldTest {

    private fun rate(text: String) = MoneyField(text, MoneyField.Kind.RATE)
    private fun payment(text: String) = MoneyField(text, MoneyField.Kind.PAYMENT)
    private fun optional(text: String) = MoneyField(text, MoneyField.Kind.OPTIONAL_RATE)

    // --- What each kind accepts ---------------------------------------------------------------

    @Test
    fun `a rate of zero is valid — teaching somebody for free is still a booked subject`() {
        assertEquals(Money.ZERO, rate("0").amount)
        assertEquals(Money.ZERO, rate("0,00").amount)
        assertTrue(rate("0").isValid)
    }

    @Test
    fun `a payment of zero is not — it would record a row that moves no money`() {
        assertNull(payment("0").amount)
        assertNull(payment("0,00").amount)
        assertFalse(payment("0").isValid)
    }

    @Test
    fun `both kinds accept the same real amounts`() {
        assertEquals(Money.ofMajor(25), rate("25").amount)
        assertEquals(Money.ofMajor(25), payment("25").amount)
        assertEquals(Money(2550), rate("25,50").amount)
        assertEquals(Money(2550), payment("25.50").amount)
    }

    @Test
    fun `blank is not an amount for a rate or a payment`() {
        assertNull(rate("").amount)
        assertNull(payment("").amount)
        assertFalse(rate("").isValid)
    }

    @Test
    fun `blank means zero — not set — for an optional rate`() {
        // This is the settings rule: clearing the box is how the default group rate is withdrawn.
        assertEquals(Money.ZERO, optional("").amount)
        assertEquals(Money.ZERO, optional("   ").amount)
        assertTrue(optional("").isValid)
    }

    @Test
    fun `junk is never an amount, whatever the kind`() {
        // Including the near-misses: a second separator, an exponent, and a number too big for
        // kopecks in a Long.
        for (text in listOf("abc", "1,2,3", "NaN", "Infinity", "1e9999", "-5")) {
            for (field in listOf(rate(text), payment(text), optional(text))) {
                assertNull("${field.kind} accepted \"$text\"", field.amount)
                assertFalse(field.isValid)
            }
        }
    }

    @Test
    fun `a thousands space is typed, not junk`() {
        assertEquals(Money.ofMajor(1000), rate("1 000").amount)
    }

    /**
     * The point of [MoneyField.Kind] is that the three kinds differ in *exactly* one text each and
     * agree about every other. Stated as a property rather than case by case, because the risk is a
     * new rule quietly diverging somewhere nobody wrote a case for — a payment that stopped accepting
     * kopecks, say.
     */
    @Test
    fun `zero and blank are the only texts the kinds disagree about`() {
        val texts = listOf("25", "25,50", "25.50", "1 000", "0,01", "abc", "NaN", "-5", "1,")

        texts.forEach { text ->
            assertEquals("rate and payment disagreed about \"$text\"", rate(text).amount, payment(text).amount)
            assertEquals("rate and optional disagreed about \"$text\"", rate(text).amount, optional(text).amount)
        }

        // ...and the two disagreements themselves, which the list above deliberately leaves out.
        assertEquals(Money.ZERO, rate("0").amount)
        assertNull("a payment of nothing is a slip", payment("0").amount)
        assertNull("a blank rate is unanswered", rate("").amount)
        assertEquals("a blank optional rate is «not set»", Money.ZERO, optional("").amount)
    }

    // --- isError: empty is incomplete, not wrong ----------------------------------------------

    @Test
    fun `an empty field is never marked wrong`() {
        assertFalse("nobody should see an error for a box they haven't reached", rate("").isError)
        assertFalse(payment("").isError)
        assertFalse(optional("").isError)
    }

    @Test
    fun `a field is marked wrong once something unusable has been typed`() {
        assertTrue(rate("abc").isError)
        assertTrue(optional("abc").isError)
        assertTrue("zero is a slip for a payment", payment("0").isError)
    }

    @Test
    fun `a valid field is never marked wrong`() {
        assertFalse(rate("25,50").isError)
        assertFalse(payment("25,50").isError)
        assertFalse(optional("25,50").isError)
    }

    // --- Prefill --------------------------------------------------------------------------------

    @Test
    fun `prefill fills an empty field`() {
        assertEquals("30", rate("").prefilledWith(Money.ofMajor(30)).text)
    }

    @Test
    fun `prefill never clobbers what the tutor already typed`() {
        assertEquals("12", rate("12").prefilledWith(Money.ofMajor(30)).text)
    }

    @Test
    fun `prefill with nothing to suggest leaves the field alone`() {
        assertEquals("", rate("").prefilledWith(null).text)
    }

    @Test
    fun `prefill keeps the kind`() {
        val field = payment("").prefilledWith(Money.ofMajor(30))
        assertEquals(MoneyField.Kind.PAYMENT, field.kind)
    }

    // --- Round-tripping ---------------------------------------------------------------------

    @Test
    fun `a field seeded from an amount reads back as that amount`() {
        for (money in listOf(Money.ZERO, Money.ofMajor(30), Money(2505), Money(1))) {
            assertEquals(money, MoneyField.of(money).amount)
        }
    }

    @Test
    fun `ofOrEmpty leaves the field empty when there is no amount`() {
        assertEquals("", MoneyField.ofOrEmpty(null).text)
        assertEquals("30", MoneyField.ofOrEmpty(Money.ofMajor(30)).text)
    }

    @Test
    fun `typed replaces the text and keeps the kind`() {
        val field = payment("10").typed("20")
        assertEquals("20", field.text)
        assertEquals(MoneyField.Kind.PAYMENT, field.kind)
    }

    @Test
    fun `half-typed input survives without being reformatted`() {
        // Re-formatting mid-typing would move the caret, so the raw text is kept verbatim even
        // once it parses: "1," is already worth 1, but the tutor may still be typing the kopecks.
        val field = rate("1,")
        assertEquals("1,", field.text)
        assertEquals(Money.ofMajor(1), field.amount)

        // A separator on its own is not an amount, and — being non-blank — does read as an error.
        assertNull(rate(",").amount)
        assertTrue(rate(",").isError)
    }
}
