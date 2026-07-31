package com.seblful.klas.ui.common

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What a choice looks like once there are too many of them to be segments.**
 *
 * A row of segments is a fixed fraction of the screen divided by the number of them, so past four there is
 * no width left to shrink a label into — `SegmentLabel` would produce four illegible words rather than an
 * honest overflow. Two of the app's choices are **lists that grow**: one option per language the app is
 * translated into and one per currency it can label money with. So "how many is too many" has to be a rule
 * the code applies rather than a sentence in a KDoc, or the fifth entry of either would ship as an
 * unreadable row — no compile error, no crash, and nothing on screen that looks deliberate.
 */
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

    /**
     * The two growing lists, as they stand — and the reason this test exists rather than the bound being
     * left as a comment. The currency row is **at** the bound today: a fifth currency changes the control,
     * which is a visible, deliberate change instead of four codes squeezed into 72 dp.
     */
    @Test
    fun `the app's two growing choices are where they are, and neither is over the bound by accident`() {
        assertEquals("BYN, RUB, USD, EUR — exactly the bound", MaxSegments, Currency.entries.size)
        assertTrue("a currency row is still segments", fitsInSegments(Currency.entries.size))
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
