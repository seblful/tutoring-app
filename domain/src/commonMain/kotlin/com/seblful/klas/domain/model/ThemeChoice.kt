package com.seblful.klas.domain.model

/**
 * Which theme the tutor asked for. «Follow the system» is a real answer rather than the absence of one —
 * so is *not yet loaded*, which also follows the system, since the alternative is a flash of the wrong
 * theme before the row arrives.
 *
 * Domain rather than `ui/`, because it is what the `settings.themeMode` column holds: the entity is typed
 * with it, and the data layer may not reach up into the screens for a type. Resolving [SYSTEM] to light or
 * dark is still the UI's (`AppRoot`), because that answer comes from the platform.
 *
 * The constant **names are persisted values**, like [Currency]'s — renaming one is a migration, and
 * `StoredEnumNamesTest` holds the acknowledgement list.
 */
enum class ThemeChoice {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    companion object {
        /**
         * The theme stored as [stored], or [SYSTEM] for anything unrecognised and for a settings row that
         * has not loaded. Total, like every parse of a stored word; falling back is safe for the reason it
         * is for a language — the wrong shade for one launch carries no money.
         */
        fun parse(stored: String?): ThemeChoice = entries.firstOrNull { it.name == stored } ?: SYSTEM
    }
}
