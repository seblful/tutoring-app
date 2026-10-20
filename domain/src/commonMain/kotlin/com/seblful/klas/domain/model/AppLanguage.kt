package com.seblful.klas.domain.model

/**
 * Which language the app speaks — chosen in Settings and in first-run setup, stored in the settings row.
 *
 * There are two translations ([RUSSIAN], [ENGLISH]) and a third answer that is not one of them: «follow
 * the phone». [SYSTEM] is a **real choice and the default**, the same shape [ThemeChoice]
 * has for light and dark, because a tutor who has set their phone to English has already said what
 * language they read software in and should not have to say it again. It is also the only answer that
 * stays right when they change their mind on the phone.
 *
 * **Unlike a currency, this carries nothing about the data.** Money is stored in bare minor units and a
 * currency only captions it, so switching *re-labels* recorded history and the settings screen confirms
 * first; a language switches the words around the same numbers and needs no question. What it does owe is
 * the calendar mirror, whose event locations are written in the app's words — see
 * `SettingsChange.calendarWork`.
 *
 * The constant **names are persisted values** — the `settings.language` column stores them — so renaming
 * one is a migration, and `StoredEnumNamesTest` holds the acknowledgement list.
 *
 * @property tag the BCP 47 language the resources are filed under, or **null** for [SYSTEM], which has no
 *   language of its own to name: it is a rule for picking one of the others.
 *
 * Languages are spoken here as **BCP 47 tags** (`ru-BY`, `en`) rather than as `java.util.Locale`, which
 * exists only on the JVM; the app turns a tag into its platform's locale at the edge (`format/Localization`).
 */
enum class AppLanguage(val tag: String?) {

    /** Whichever of the translations the phone asks for, [FALLBACK] when it asks for neither. */
    SYSTEM(null),

    /** `values-ru/`. */
    RUSSIAN("ru"),

    /** `values/`, which is also where any other phone language lands. */
    ENGLISH("en"),
    ;

    /**
     * The language to actually display in, as a tag, given the phone's [preferred] languages (BCP 47 tags,
     * most-wanted first).
     *
     * The whole of what [SYSTEM] means, in one place: every other value answers with itself. One answer for
     * both of its callers — the resources the strings come from and the formatters the dates come from —
     * so the two cannot resolve it differently.
     */
    fun tagFor(preferred: List<String>): String = tag ?: of(preferred).tag!!

    companion object {

        /**
         * Where a phone speaking neither Russian nor English lands.
         *
         * It is [ENGLISH] because `values/` is English: Android's own fallback for an unmatched locale is
         * the default resource folder, so making this anything else would put the strings and the dates in
         * different languages on a French phone.
         */
        val FALLBACK = ENGLISH

        /** The languages the app is actually written in — [SYSTEM] is not one of them. */
        val translated: List<AppLanguage> = entries.filter { it.tag != null }

        /**
         * Which translation [preferred] asks for, [FALLBACK] when it asks for neither.
         *
         * Matched on the **language** alone — a tag's first subtag, case aside — so `ru-BY` and `ru-RU` are
         * both Russian: a region is not a language, and a Minsk phone must not fall through to English for
         * want of a `values-ru-rBY/`.
         */
        fun of(preferred: List<String>): AppLanguage =
            preferred.firstNotNullOfOrNull { tag ->
                val language = tag.substringBefore('-').lowercase()
                translated.firstOrNull { it.tag == language }
            } ?: FALLBACK

        /**
         * The language stored as [stored], or [SYSTEM] for anything this build does not know — including a
         * row that has not loaded yet.
         *
         * Total, like every parse of a stored word: the read path is inside a settings flow where a throw
         * reports nothing. Falling back is safe here for the reason it is for a currency and is not for a
         * class status — an unreadable language costs the tutor the wrong words for one launch, and carries
         * no money.
         */
        fun parse(stored: String?): AppLanguage = entries.firstOrNull { it.name == stored } ?: SYSTEM
    }
}
