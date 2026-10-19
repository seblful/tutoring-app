package com.seblful.klas.domain.model

import kotlin.jvm.JvmInline

/**
 * An amount of money, stored as an integer number of minor units (kopecks, cents) to avoid
 * floating-point rounding. Displayed with the currency's ISO code after it, e.g. `1 500 BYN`.
 *
 * **The amount carries no currency**, deliberately: which one it is captioned with is a display setting
 * ([Currency]), so the same stored number reads as rubles or as dollars without being touched.
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
     *
     * [decimal] has **no default**, for the reason `hour12` has none: it is a fact about how the reader
     * writes numbers, not something this class can know, and a default is how the wrong one gets picked
     * everywhere at once. Russian writes `1500,50` and English `1500.50`; the app's own answer is its
     * chosen language's, supplied once by `format/AppLocale` so no screen has to decide. `parseMoneyOrNull`
     * deliberately accepts either, so a field seeded one way still reads if the language changes under it.
     */
    fun amountText(decimal: Char): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        val major = abs / 100
        val kopecks = (abs % 100).toInt()
        return if (kopecks == 0) "$sign$major" else "$sign$major$decimal${kopecks.toString().padStart(2, '0')}"
    }

    /**
     * The amount as a reader is shown it — [amountText] with the whole part in groups of three, e.g.
     * `16 037,50` or `16,037.50`. A five-figure total without them reads as a string of digits.
     *
     * **Display only.** [amountText] stays what a text field is seeded with, because a typed amount's comma
     * is a decimal separator to `parseMoneyOrNull`: an English field seeded `1,500` would read back as 1.5.
     * [grouping] has no default for the reason [amountText]'s separator has none.
     */
    fun displayText(decimal: Char, grouping: Char): String {
        val plain = amountText(decimal)
        val sign = if (plain.startsWith('-')) "-" else ""
        val digits = plain.removePrefix(sign)
        val whole = digits.substringBefore(decimal)
        val fraction = digits.substring(whole.length)
        val grouped = whole.reversed().chunked(DIGITS_PER_GROUP).joinToString(grouping.toString()).reversed()
        return "$sign$grouped$fraction"
    }

    /** Formats as an amount in [currency], e.g. `1 500 BYN` or `1,500.50 USD`. See [displayText]. */
    fun format(currency: Currency, decimal: Char, grouping: Char): String =
        "${displayText(decimal, grouping)} ${currency.name}"

    companion object {
        val ZERO = Money(0)

        private const val DIGITS_PER_GROUP = 3

        /**
         * The largest rate or payment accepted from input, 1 000 000,00 — enforced by
         * `parseMoneyOrNull` for typed values and by `BackupIntegrity` on import.
         *
         * A bound is needed because `Money` is a `Long` and the arithmetic downstream **multiplies**:
         * `ClassRules.chargeFor` computes `rate × minutes`, and `Reports.percentOf` computes
         * `part × 100`. Nothing capped a typed rate, so a field the app called valid could bill a
         * *negative* charge — 2 000 000 000 000 000 accepted, times 60 minutes, wrapped past
         * `Long.MAX_VALUE` and came back owing the student money. Rejecting the input is the only
         * place the problem is still legible: every later stage sees a plain number that has already
         * lost the truth.
         *
         * The ceiling bounds rates and payments, not derived charges or totals. A stored lesson's
         * duration may be any positive Int, so `1e8 × Int.MAX_VALUE` fits in a Long before the
         * hourly charge is divided by 60. `BackupIntegrity` permits that derived charge above MAX.
         * Money itself imposes no bound, and summing charges or scaling totals still needs its own
         * overflow analysis; an input ceiling alone does not bound a lifetime's income.
         */
        val MAX = Money(100_000_000)

        /** Builds a [Money] from a whole-unit amount — 5 is `5,00`. */
        fun ofMajor(major: Long): Money = Money(major * 100)
    }
}
