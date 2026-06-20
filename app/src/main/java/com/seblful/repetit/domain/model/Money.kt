package com.seblful.repetit.domain.model

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

    /** Formats as a localized amount in [currency], e.g. `1500 BYN` or `1500,50 RUB`. */
    fun format(currency: String = "BYN"): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        val major = abs / 100
        val kopecks = (abs % 100).toInt()
        return if (kopecks == 0) {
            "$sign$major $currency"
        } else {
            "$sign$major,${kopecks.toString().padStart(2, '0')} $currency"
        }
    }

    companion object {
        val ZERO = Money(0)

        /** Builds a [Money] from a whole-ruble amount. */
        fun ofMajor(major: Long): Money = Money(major * 100)
    }
}
