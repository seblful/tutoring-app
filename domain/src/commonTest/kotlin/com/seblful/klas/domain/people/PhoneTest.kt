package com.seblful.klas.domain.people

import com.seblful.klas.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneTest {

    @Test
    fun `the field opens on the dialling code of the country issuing the currency`() {
        assertEquals("+375 ", Phone.prefill(Currency.BYN))
        assertEquals("+7 ", Phone.prefill(Currency.RUB))
        assertEquals("+1 ", Phone.prefill(Currency.USD))
        assertEquals("+44 ", Phone.prefill(Currency.GBP))
    }

    @Test
    fun `the euro names no one country - its field opens empty`() {
        assertEquals("", Phone.prefill(Currency.EUR))
    }

    @Test
    fun `every currency but the euro has a code of a plus and one to three digits`() {
        Currency.entries.filter { it != Currency.EUR }.forEach { currency ->
            val code = Phone.callingCode(currency)
            assertTrue(code != null && Regex("""\+\d{1,3}""").matches(code), "${currency.name}: $code")
        }
    }

    @Test
    fun `a field left on any dialling code keeps no phone`() {
        Currency.entries.forEach { assertEquals("", Phone.stored(Phone.prefill(it)), it.name) }
        assertEquals("", Phone.stored(" +375 "))
        assertEquals("", Phone.stored("+375"))
        assertEquals("", Phone.stored("+"))
    }

    @Test
    fun `a typed number is kept - trimmed`() {
        assertEquals("+375 29 123-45-67", Phone.stored("  +375 29 123-45-67 "))
        assertEquals("+7 9161234567", Phone.stored("+7 9161234567"))
        assertEquals("8 029 1234567", Phone.stored("8 029 1234567"))
    }

    @Test
    fun `an empty field stays empty`() {
        assertEquals("", Phone.stored("   "))
    }

    @Test
    fun `a prefix-only phone already kept is not a contact`() {
        assertNull(Phone.contactOf("+375"))
        assertNull(Phone.contactOf(""))
        assertEquals("+375 29 1234567", Phone.contactOf("+375 29 1234567"))
    }

    @Test
    fun `an edit opens on the number - or on the dialling code of the currency when there is none`() {
        assertEquals("+7 ", Phone.editable("", Currency.RUB))
        assertEquals("+7 ", Phone.editable("+375", Currency.RUB))
        assertEquals("+375 29 1234567", Phone.editable("+375 29 1234567", Currency.RUB))
    }
}
