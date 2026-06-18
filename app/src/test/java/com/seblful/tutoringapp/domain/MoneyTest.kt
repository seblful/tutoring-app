package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats whole rubles without decimals`() {
        assertEquals("1500 Br", Money.ofMajor(1500).format())
    }

    @Test
    fun `formats kopecks with two decimal places`() {
        assertEquals("1500,50 Br", Money(150050).format())
        assertEquals("0,05 Br", Money(5).format())
    }

    @Test
    fun `formats negative amounts with a leading sign`() {
        assertEquals("-300 Br", Money.ofMajor(-300).format())
    }

    @Test
    fun `arithmetic adds and subtracts minor units`() {
        assertEquals(Money(300), Money(100) + Money(200))
        assertEquals(Money(-50), Money(100) - Money(150))
        assertTrue((Money(100) - Money(150)).isNegative)
    }
}
