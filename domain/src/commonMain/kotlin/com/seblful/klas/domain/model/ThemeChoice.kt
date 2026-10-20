package com.seblful.klas.domain.model

/**
 * Theme preference stored in settings. [SYSTEM] is also the safe default while settings load.
 *
 * Domain rather than `ui/`, because it is what the `settings.themeMode` column holds: the entity is typed
 * with it, and the data layer may not reach up into the screens for a type. Resolving [SYSTEM] to light or
 * dark is still the UI's (`AppRoot`), because that answer comes from the platform.
 *
 * Constant names are persisted values; renaming one requires a migration.
 */
enum class ThemeChoice {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    companion object {
        /**
         * The stored theme, or [SYSTEM] when the value is missing or unrecognized.
         */
        fun parse(stored: String?): ThemeChoice = entries.firstOrNull { it.name == stored } ?: SYSTEM
    }
}
