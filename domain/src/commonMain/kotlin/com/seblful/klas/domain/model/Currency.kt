package com.seblful.klas.domain.model

/** Currency names are persisted; switching currency relabels stored amounts but never converts them. */
enum class Currency(val symbol: String) {
    // Provisionally assigned by Unicode; the app bundles the glyph in its fonts.
    BYN("\u20C5"),
    RUB("₽"),
    USD("$"),
    EUR("€"),
    AED("AED"),
    AUD("A$"),
    BRL("R$"),
    CAD("C$"),
    CHF("CHF"),
    CNY("CN¥"),
    CZK("Kč"),
    DKK("DKK"),
    GBP("£"),
    HKD("HK$"),
    INR("₹"),
    JPY("¥"),
    KRW("₩"),
    KZT("₸"),
    MXN("MX$"),
    NOK("NOK"),
    NZD("NZ$"),
    PLN("zł"),
    SAR("SAR"),
    SEK("SEK"),
    SGD("S$"),
    TRY("₺"),
    UAH("₴"),
    ZAR("R"),
    ;

    companion object {
        fun parse(stored: String): Currency = entries.firstOrNull { it.name == stored } ?: BYN

        val FALLBACK = USD

        fun forCode(code: String?): Currency? = entries.firstOrNull { it.name == code }

        /** Chooses the first supported [regionCurrencies] entry, or [FALLBACK]. */
        fun preferredFor(regionCurrencies: List<String?>): Currency =
            regionCurrencies.firstNotNullOfOrNull(::forCode) ?: FALLBACK
    }
}
