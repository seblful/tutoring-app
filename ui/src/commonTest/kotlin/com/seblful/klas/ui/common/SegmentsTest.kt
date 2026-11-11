package com.seblful.klas.ui.common

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentsTest {

    @Test
    fun `a choice of up to four values is asked with segments`() {
        assertTrue(fitsInSegments(2), "a two-way switch")
        assertTrue(fitsInSegments(3))
        assertTrue(fitsInSegments(MaxSegments), "the bound itself")
    }

    @Test
    fun `past that it is asked with a field`() {
        assertFalse(fitsInSegments(MaxSegments + 1), "one past the bound")
        assertFalse(fitsInSegments(9))
    }

    @Test
    fun `currencies and languages need dropdowns`() {
        assertFalse(fitsInSegments(Currency.entries.size), "currencies use a dropdown")
        assertFalse(
            fitsInSegments(AppLanguage.entries.size),
            "Follow the system plus 25 translations needs a scrollable list",
        )
        assertEquals(
            AppLanguage.translated.size + 1,
            AppLanguage.entries.size,
            "The language row includes SYSTEM and every translation",
        )
    }
}
