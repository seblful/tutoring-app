package com.seblful.klas.text

/*
 * Keys and tables of the generated text catalog (`Strings`, `Plurals`, `StringArrays`), which the
 * `klas.strings` build plugin writes from `src/commonMain/strings/values…/strings.xml`.
 */

/** A string in every language the app speaks; read it with [Texts.string] or `stringResource`. */
class StringResource internal constructor(internal val index: Int, val key: String) {
    override fun equals(other: Any?) = other is StringResource && other.index == index

    override fun hashCode() = index

    override fun toString() = "Strings.$key"
}

/** A count's text, chosen by the language's plural rules; read it with [Texts.plural]. */
class PluralResource internal constructor(internal val index: Int, val key: String) {
    override fun equals(other: Any?) = other is PluralResource && other.index == index

    override fun hashCode() = index

    override fun toString() = "Plurals.$key"
}

/** A list of strings, such as weekday names; read it with [Texts.array]. */
class StringArrayResource internal constructor(internal val index: Int, val key: String) {
    override fun equals(other: Any?) = other is StringArrayResource && other.index == index

    override fun hashCode() = index

    override fun toString() = "StringArrays.$key"
}

/** One plural's forms in one language; a form the language does not write is null and reads as [other]. */
internal class PluralText(
    val zero: String? = null,
    val one: String? = null,
    val two: String? = null,
    val few: String? = null,
    val many: String? = null,
    val other: String,
) {
    fun forCategory(category: PluralCategory): String = when (category) {
        PluralCategory.ZERO -> zero
        PluralCategory.ONE -> one
        PluralCategory.TWO -> two
        PluralCategory.FEW -> few
        PluralCategory.MANY -> many
        PluralCategory.OTHER -> other
    } ?: other
}

/** One language's texts, indexed by the keys; null where the language has no translation. */
internal abstract class TextTable(val tag: String) {
    abstract val strings: Array<String?>
    abstract val plurals: Array<PluralText?>
    abstract val arrays: Array<List<String>?>
}
