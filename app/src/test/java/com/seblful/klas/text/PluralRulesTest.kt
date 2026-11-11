package com.seblful.klas.text

import android.icu.text.PluralRules
import android.icu.util.ULocale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The catalog's plural rules are written out by hand so iOS chooses forms without ICU; this holds them to
 * the ICU Android itself uses, count by count, in every language the app is translated into.
 */
@RunWith(RobolectricTestRunner::class)
class PluralRulesTest {

    @Test
    fun everyLanguageChoosesTheFormIcuChooses() {
        val counts = (0..2_100) + listOf(10_000, 100_000, 999_999, 1_000_000, 2_000_000, 1_000_001, Int.MAX_VALUE)
        for (tag in Texts.tags) {
            val icu = PluralRules.forLocale(ULocale.forLanguageTag(tag))
            val texts = Texts.of(tag)
            for (count in counts) {
                assertEquals("$tag $count", icu.select(count.toDouble()), texts.pluralCategory(count).name.lowercase())
            }
        }
    }
}
