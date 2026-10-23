package com.seblful.klas.ui.common

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentsTest {

    @Test
    fun `a choice of up to four values is asked with segments`() {
        assertTrue("a two-way switch", fitsInSegments(2))
        assertTrue(fitsInSegments(3))
        assertTrue("the bound itself", fitsInSegments(MaxSegments))
    }

    @Test
    fun `past that it is asked with a field`() {
        assertFalse("one past the bound", fitsInSegments(MaxSegments + 1))
        assertFalse(fitsInSegments(9))
    }

    @Test
    fun `currencies and languages need dropdowns`() {
        assertFalse("currencies use a dropdown", fitsInSegments(Currency.entries.size))
        assertFalse(
            "Follow the system plus 25 translations needs a scrollable list",
            fitsInSegments(AppLanguage.entries.size),
        )
        assertEquals(
            "The language row includes SYSTEM and every translation",
            AppLanguage.translated.size + 1,
            AppLanguage.entries.size,
        )
    }
}
