package com.seblful.klas.domain.model

import java.util.Locale

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
 */
enum class AppLanguage(val tag: String?) {

    /** Whichever of the translations the phone asks for, [FALLBACK] when it asks for neither. */
    SYSTEM(null),

    /** `values-ru/`. */
    RUSSIAN("ru"),

    /** `values/`, which is also where any other phone language lands. */
    ENGLISH("en");

    /** The locale this language *is*, or null for [SYSTEM]. See [localeFor] for the resolved answer. */
    val locale: Locale? get() = tag?.let(Locale::forLanguageTag)

    /**
     * The locale to actually display in, given the phone's [preferred] locales (most-wanted first).
     *
     * The whole of what [SYSTEM] means, in one place: every other value answers with itself. Returns a
     * `Locale` rather than an [AppLanguage] because that is what a `Configuration` and a
     * `DateTimeFormatter` both take, and because the two callers — the resources the strings come from and
     * the formatters the dates come from — must not be able to resolve it differently.
     */
    fun localeFor(preferred: List<Locale>): Locale = locale ?: of(preferred).locale!!

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
         * Matched on the **language** alone, so `ru-BY` and `ru-RU` are both Russian: a region is not a
         * language, and a Minsk phone must not fall through to English for want of a `values-ru-rBY/`.
         */
        fun of(preferred: List<Locale>): AppLanguage =
            preferred.firstNotNullOfOrNull { locale ->
                translated.firstOrNull { it.tag == locale.language }
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
