package com.seblful.klas.text

import com.seblful.klas.domain.model.AppLanguage

/**
 * The app's words in one language: what screens read through `LocalTexts` and what a notification, widget
 * or calendar event reads directly. The language is always an explicit choice, never the process locale —
 * Settings can pick one other than the phone's, and background work speaks the stored one.
 *
 * Lookups mirror Android's: a string without arguments is returned as written (`%%` stays), one with
 * arguments is formatted; a key a translation lacks reads in [AppLanguage.FALLBACK].
 */
class Texts private constructor(private val table: TextTable) {

    /** The BCP 47 tag of the table actually read, one of [AppLanguage.translated]'s. */
    val tag: String get() = table.tag

    fun string(resource: StringResource): String =
        table.strings[resource.index] ?: fallback.strings[resource.index]!!

    fun string(resource: StringResource, vararg args: Any?): String = formatText(string(resource), tag, args)

    fun plural(resource: PluralResource, count: Int): String = pluralText(resource, count)

    fun plural(resource: PluralResource, count: Int, vararg args: Any?): String =
        formatText(pluralText(resource, count), tag, args)

    /** The form [count] takes in this language. */
    fun pluralCategory(count: Int): PluralCategory = PluralRules.select(tag, count.toLong())

    fun array(resource: StringArrayResource): List<String> =
        table.arrays[resource.index] ?: fallback.arrays[resource.index]!!

    private fun pluralText(resource: PluralResource, count: Int): String {
        val own = table.plurals[resource.index]
        return own?.forCategory(PluralRules.select(tag, count.toLong()))
            ?: fallback.plurals[resource.index]!!.forCategory(PluralRules.select(DEFAULT_TEXT_TAG, count.toLong()))
    }

    override fun toString() = "Texts($tag)"

    companion object {
        private val fallback: TextTable get() = textTable(DEFAULT_TEXT_TAG)!!

        /**
         * The texts for [tag] as [AppLanguage] resolves it: `ru`, `ru-BY` → Russian, an untranslated
         * language → [AppLanguage.FALLBACK]. Cheap: each language's table loads once, on first use.
         */
        fun of(tag: String): Texts = Texts(textTable(AppLanguage.of(listOf(tag)).tag!!) ?: fallback)

        /** [language] resolved against the phone's [preferred] tags. */
        fun of(language: AppLanguage, preferred: List<String>): Texts = of(language.tagFor(preferred))

        /** Every language with a table, for tests and tooling. */
        val tags: Set<String> get() = textTags
    }
}
