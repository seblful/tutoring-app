package com.seblful.klas.domain.model

/**
 * An amount of money in BYN, stored as an integer number of minor units (kopecks)
 * to avoid floating-point rounding. Displayed with the "BYN" sign, e.g. `1500 BYN`.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(minor + other.minor)
    operator fun minus(other: Money) = Money(minor - other.minor)

    val isNegative: Boolean get() = minor < 0
    val isZero: Boolean get() = minor == 0L

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    /**
     * The amount on its own, with no currency sign — e.g. `1500`, `1500,50`, `-0,50`. The one
     * definition of how an amount reads: [format] adds a currency to it, and `Money.toAmountInput`
     * in `format/` is what a text field is seeded with, so an amount can't render two ways.
     */
    val amountText: String
        get() {
            val sign = if (minor < 0) "-" else ""
            val abs = kotlin.math.abs(minor)
            val major = abs / 100
            val kopecks = (abs % 100).toInt()
            return if (kopecks == 0) "$sign$major" else "$sign$major,${kopecks.toString().padStart(2, '0')}"
        }

    /** Formats as a localized amount in [currency], e.g. `1500 BYN` or `1500,50 RUB`. */
    fun format(currency: Currency = Currency.BYN): String = "$amountText ${currency.name}"

    companion object {
        val ZERO = Money(0)

        /**
         * The largest amount the app accepts, 1 000 000,00 — enforced once, at the parse gate every
         * typed amount goes through (`parseMoneyOrNull`).
         *
         * A bound is needed because `Money` is a `Long` and the arithmetic downstream **multiplies**:
         * `ClassRules.chargeFor` computes `rate × minutes`, and `Reports.percentOf` computes
         * `part × 100`. Nothing capped a typed rate, so a field the app called valid could bill a
         * *negative* charge — 2 000 000 000 000 000 accepted, times 60 minutes, wrapped past
         * `Long.MAX_VALUE` and came back owing the student money. Rejecting the input is the only
         * place the problem is still legible: every later stage sees a plain number that has already
         * lost the truth.
         *
         * This value is what makes the multiplications safe rather than merely unlikely. A lesson
         * lasts under a day (1 439 minutes), so the largest charge is `1e8 × 1439 ≈ 1.4e11`; a
         * lifetime of those summed stays around `1e16`, and `× 100` for a percentage stays under
         * `9.2e18`. It is also far above any hourly rate or payment a tutor will type, so the ceiling
         * is only ever met by a slip or by a hostile file.
         */
        val MAX = Money(100_000_000)

        /** Builds a [Money] from a whole-ruble amount. */
        fun ofMajor(major: Long): Money = Money(major * 100)
    }
}
