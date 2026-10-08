package com.seblful.klas.domain.model

import java.util.Locale

/**
 * The currencies money can be displayed in — chosen in Settings and in first-run setup, provided to the
 * UI through `LocalCurrency`.
 *
 * A **display setting, not a fact about the data**: amounts are stored as bare minor units, so
 * switching re-labels recorded history rather than converting it — which is why the settings screen
 * confirms first. The legal set lives here, once; it used to be stated only inside a composable,
 * with five copies of the `"BYN"` default spread across four modules and no place a test could ask
 * what currencies the app supports.
 *
 * **All four are printed as their ISO code**, not as a symbol ([Money.format] appends [name]). That is
 * the same argument that keeps them un-converted: the app is captioning a number it was handed, and «$»
 * claims a currency the way «BYN» merely names one — the same amount would read as US dollars on a phone
 * whose owner meant Australian ones. A code is also the one spelling that is unambiguous in both
 * languages the app speaks.
 *
 * The constant **names are persisted values** — the `settings.currency` column stores them — so
 * renaming one is a migration, and `StoredEnumNamesTest` holds the acknowledgement list.
 */
enum class Currency {
    BYN,
    RUB,
    USD,
    EUR;

    companion object {
        /**
         * The currency stored as [stored], or [BYN] when that is not one of these names. Total, like
         * every parse of a stored word — the read path is inside a settings flow, where a throw
         * reports nothing. Falling back is safe here in the way it is not for a class status: a
         * currency label carries no money of its own, it only captions amounts that stay intact.
         */
        fun parse(stored: String): Currency = entries.firstOrNull { it.name == stored } ?: BYN

        /**
         * Where a phone that names no currency this app knows lands.
         *
         * [USD] rather than [BYN], which is only the first currency the app ever had: this is the answer
         * for a tutor the app knows nothing about beyond the fact that their region is not one of the four,
         * and a dollar is the amount they are most likely to recognise as "a placeholder I should change".
         */
        val FALLBACK = USD

        /**
         * The currency [locale]'s **region** uses, when the app supports it — `ru-BY` is [BYN], `en-IE` is
         * [EUR], `en-GB` is neither and so is null.
         *
         * Read off the region rather than the language, which is the whole point: a tutor in Minsk with an
         * English phone is paid in rubles. The mapping is `java.util.Currency`'s, so the eurozone does not
         * have to be listed here and cannot go stale; a locale with no country (or one the platform has
         * never heard of) throws, and that is a missing answer rather than a crash.
         */
        fun forLocale(locale: Locale): Currency? =
            runCatching { java.util.Currency.getInstance(locale).currencyCode }
                .getOrNull()
                ?.let { code -> entries.firstOrNull { it.name == code } }

        /**
         * What a phone offering [preferred] (most-wanted first) should start on, [FALLBACK] when none of
         * them names a currency the app has.
         *
         * **This is what a fresh install's money is captioned in**, applied where a settings row that does
         * not exist yet is mapped to defaults (`SettingsRepository`) — so the first amount a tutor types is
         * already labelled in their own currency, and first-run setup has something true to pre-select
         * rather than a guess to make them correct.
         */
        fun preferredFor(preferred: List<Locale>): Currency =
            preferred.firstNotNullOfOrNull(::forLocale) ?: FALLBACK
    }
}
