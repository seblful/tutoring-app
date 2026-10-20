package com.seblful.klas.format

import com.seblful.klas.domain.model.Money

/** Plain non-negative decimal input, rounded half up to minor units; accepts either separator. */
fun parseMoneyOrNull(text: String): Money? {
    val normalized = text.trim().replace(',', '.').filterNot { it.isWhitespace() }
    if (!PLAIN_AMOUNT.matches(normalized)) return null

    // Reject overflow and the amount ceiling before multiplying whole units into minor units.
    val whole = normalized.substringBefore('.').ifEmpty { "0" }.toLongOrNull()
        ?.takeIf { it <= Money.MAX.minor / MINOR_PER_MAJOR }
    val fraction = normalized.substringAfter('.', "")
    val minor = fraction.take(DECIMAL_PLACES).padEnd(DECIMAL_PLACES, '0').toInt() +
        if ((fraction.getOrNull(DECIMAL_PLACES) ?: '0') >= '5') 1 else 0
    return whole?.let { Money(it * MINOR_PER_MAJOR + minor).takeIf { amount -> amount <= Money.MAX } }
}

private const val MINOR_PER_MAJOR = 100L
private const val DECIMAL_PLACES = 2
private val PLAIN_AMOUNT = Regex("""[0-9]+(\.[0-9]*)?|\.[0-9]+""")
