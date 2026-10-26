package com.seblful.klas.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MonogramTest {

    @Test
    fun `a monogram is the first letter, in capitals`() {
        assertEquals("А", monogram("аня Смирнова"))
        assertEquals("A", monogram("  anna "))
    }

    @Test
    fun `a nameless row shows a question mark`() {
        assertEquals("?", monogram(""))
        assertEquals("?", monogram("   "))
    }

    @Test
    fun `a name that starts outside the basic plane keeps its whole first character`() {
        assertEquals("🦄", monogram("🦄 Mia"))
        assertEquals("𠀋", monogram("𠀋字"))
    }
}
