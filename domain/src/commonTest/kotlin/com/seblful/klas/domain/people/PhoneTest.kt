package com.seblful.klas.domain.people

import com.seblful.klas.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneTest {

    @Test
    fun `every currency of one country has a code of a plus and one to three digits`() {
        (Currency.entries - Phone.SHARED_ACROSS_CODES).forEach { currency ->
            val code = Phone.callingCode(currency)
            assertTrue(code != null && Regex("""\+\d{1,3}""").matches(code), "${currency.name}: $code")
        }
    }

    @Test
    fun `a currency of several countries names no one code`() {
        Phone.SHARED_ACROSS_CODES.forEach { assertNull(Phone.callingCode(it), it.name) }
        assertEquals("+84", Phone.callingCode(Currency.VND))
    }

    @Test
    fun `a field holding only a dialling code keeps no phone`() {
        assertEquals("", Phone.stored(" +375 "))
        assertEquals("", Phone.stored("+375"))
        assertEquals("", Phone.stored("+"))
        assertEquals("", Phone.stored("   "))
    }

    @Test
    fun `a typed number is kept - trimmed`() {
        assertEquals("+375 29 123-45-67", Phone.stored("  +375 29 123-45-67 "))
        assertEquals("8 029 1234567", Phone.stored("8 029 1234567"))
    }

    @Test
    fun `a prefix-only phone already kept is not a contact`() {
        assertNull(Phone.contactOf("+375"))
        assertNull(Phone.contactOf(""))
        assertEquals("+375 29 1234567", Phone.contactOf("+375 29 1234567"))
    }

    @Test
    fun `a number with a code is reached as it is`() {
        assertEquals("+375291234567", Phone.international("+375 (29) 123-45-67", Currency.RUB))
        assertEquals("+375291234567", Phone.international("00375 29 123-45-67", Currency.RUB))
    }

    @Test
    fun `a national number takes the code of the tutor's country, without its trunk prefix`() {
        assertEquals("+375291234567", Phone.international("29 123-45-67", Currency.BYN))
        assertEquals("+375291234567", Phone.international("8 029 123-45-67", Currency.BYN))
        assertEquals("+79161234567", Phone.international("8 916 123-45-67", Currency.RUB))
        assertEquals("+447911123456", Phone.international("07911 123456", Currency.GBP))
        assertEquals("+15551234567", Phone.international("555 123-4567", Currency.USD))
    }

    @Test
    fun `no country to put on a national number means nothing to hand to a messenger`() {
        assertNull(Phone.international("29 123-45-67", Currency.EUR))
        assertEquals("+375291234567", Phone.international("+375 29 123-45-67", Currency.EUR))
        assertNull(Phone.international("", Currency.BYN))
        assertNull(Phone.international("+375", Currency.BYN))
    }
}
