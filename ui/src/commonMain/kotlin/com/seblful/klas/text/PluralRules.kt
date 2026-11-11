// The numbers are CLDR's rule text, kept verbatim so each rule can be read against its source.
@file:Suppress("MagicNumber")

package com.seblful.klas.text

import kotlin.math.absoluteValue

/** CLDR plural categories. */
enum class PluralCategory { ZERO, ONE, TWO, FEW, MANY, OTHER }

/**
 * CLDR's cardinal rules for whole numbers, in the languages the app is translated into — the same rules
 * Android's ICU applies to `getQuantityString`. `PluralRulesTest` in `:app` holds them to ICU, number by
 * number, so iOS chooses the forms Android does.
 */
internal object PluralRules {

    fun select(tag: String, count: Long): PluralCategory {
        val n = count.absoluteValue
        return when (tag) {
            "ar" -> arabic(n)
            "be", "ru", "uk" -> eastSlavic(n)
            "pl" -> polish(n)
            "fr", "pt-BR" -> oneUpToOne(n)
            "es", "it", "pt-PT" -> oneAtOneManyAtMillions(n)
            "hi" -> if (n <= 1) PluralCategory.ONE else PluralCategory.OTHER
            "ja", "ko", "zh-CN", "zh-TW" -> PluralCategory.OTHER
            else -> oneAtOne(n)
        }
    }

    private fun arabic(n: Long): PluralCategory = when {
        n == 0L -> PluralCategory.ZERO
        n == 1L -> PluralCategory.ONE
        n == 2L -> PluralCategory.TWO
        n % 100 in 3..10 -> PluralCategory.FEW
        n % 100 in 11..99 -> PluralCategory.MANY
        else -> PluralCategory.OTHER
    }

    private fun eastSlavic(n: Long): PluralCategory = when {
        n % 10 == 1L && n % 100 != 11L -> PluralCategory.ONE
        fewInSlavic(n) -> PluralCategory.FEW
        else -> PluralCategory.MANY
    }

    private fun polish(n: Long): PluralCategory = when {
        n == 1L -> PluralCategory.ONE
        fewInSlavic(n) -> PluralCategory.FEW
        else -> PluralCategory.MANY
    }

    /** French and Brazilian Portuguese: 0 and 1 are singular; whole millions take `many`. */
    private fun oneUpToOne(n: Long): PluralCategory = when {
        n <= 1 -> PluralCategory.ONE
        millions(n) -> PluralCategory.MANY
        else -> PluralCategory.OTHER
    }

    /** Spanish, Italian and European Portuguese: only 1 is singular; whole millions take `many`. */
    private fun oneAtOneManyAtMillions(n: Long): PluralCategory = when {
        n == 1L -> PluralCategory.ONE
        millions(n) -> PluralCategory.MANY
        else -> PluralCategory.OTHER
    }

    private fun oneAtOne(n: Long): PluralCategory = if (n == 1L) PluralCategory.ONE else PluralCategory.OTHER

    private fun fewInSlavic(n: Long) = n % 10 in 2..4 && n % 100 !in 12..14

    private fun millions(n: Long) = n != 0L && n % 1_000_000 == 0L
}
