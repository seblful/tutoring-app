package com.seblful.klas.domain.people

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneTest {

    @Test
    fun `a field left on its dialling code keeps no phone`() {
        assertEquals("", Phone.stored(Phone.PREFILL))
        assertEquals("", Phone.stored(" +375 "))
        assertEquals("", Phone.stored("+375"))
    }

    @Test
    fun `a typed number is kept, trimmed`() {
        assertEquals("+375 29 123-45-67", Phone.stored("  +375 29 123-45-67 "))
        assertEquals("8 029 1234567", Phone.stored("8 029 1234567"))
    }

    @Test
    fun `an empty field stays empty`() {
        assertEquals("", Phone.stored("   "))
    }

    /** A card built from a row an earlier build saved as «+375» has no number to offer. */
    @Test
    fun `a prefix-only phone already kept is not a contact`() {
        assertNull(Phone.contactOf("+375"))
        assertNull(Phone.contactOf(""))
        assertEquals("+375 29 1234567", Phone.contactOf("+375 29 1234567"))
    }

    @Test
    fun `an edit opens on the number, or on the dialling code when there is none`() {
        assertEquals(Phone.PREFILL, Phone.editable(""))
        assertEquals(Phone.PREFILL, Phone.editable("+375"))
        assertEquals("+375 29 1234567", Phone.editable("+375 29 1234567"))
    }
}
