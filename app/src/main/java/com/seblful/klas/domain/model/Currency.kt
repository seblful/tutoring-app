package com.seblful.klas.domain.model

/**
 * The currencies money can be displayed in — chosen in Settings, provided to the UI through
 * `LocalCurrency`.
 *
 * A **display setting, not a fact about the data**: amounts are stored as bare minor units, so
 * switching re-labels recorded history rather than converting it — which is why the settings screen
 * confirms first. The legal set lives here, once; it used to be stated only inside a composable,
 * with five copies of the `"BYN"` default spread across four modules and no place a test could ask
 * what currencies the app supports.
 *
 * The constant **names are persisted values** — the `settings.currency` column stores them — so
 * renaming one is a migration, and `StoredEnumNamesTest` holds the acknowledgement list.
 */
enum class Currency {
    BYN,
    RUB;

    companion object {
        /**
         * The currency stored as [stored], or [BYN] when that is not one of these names. Total, like
         * every parse of a stored word — the read path is inside a settings flow, where a throw
         * reports nothing. Falling back is safe here in the way it is not for a class status: a
         * currency label carries no money of its own, it only captions amounts that stay intact.
         */
        fun parse(stored: String): Currency = entries.firstOrNull { it.name == stored } ?: BYN
    }
}
