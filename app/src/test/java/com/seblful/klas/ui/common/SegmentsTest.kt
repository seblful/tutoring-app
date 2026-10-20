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
    fun `currencies need a dropdown while the language choice fits in segments`() {
        assertFalse("currencies use a dropdown", fitsInSegments(Currency.entries.size))
        assertTrue(
            "«follow the system» plus two translations",
            fitsInSegments(AppLanguage.entries.size),
        )
        assertEquals(
            "a language row is SYSTEM plus one segment per translation, so a third translation is the " +
                "bound and a fourth turns the row into a field — which is the intended behaviour, not a " +
                "reason to widen it",
            AppLanguage.translated.size + 1,
            AppLanguage.entries.size,
        )
    }
}
