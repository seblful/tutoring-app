package com.seblful.klas.format

import com.seblful.klas.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParsingLimitsTest {
    @Test
    fun `huge positive exponents are invalid money rather than an arithmetic exception`() {
        assertNull(parseMoneyOrNull("1e2147483647"))
        assertNull(parseMoneyOrNull("1e100000000"))
    }

    @Test
    fun `tiny scientific values round to zero without expanding their scale`() {
        assertEquals(Money.ZERO, parseMoneyOrNull("1e-2147483647"))
        assertEquals(Money.ZERO, parseMoneyOrNull("1e-100000000"))
        assertEquals(Money(1), parseMoneyOrNull("0.005"))
        assertEquals(Money.MAX, parseMoneyOrNull("1000000.004"))
    }
}
