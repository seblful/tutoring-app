package com.seblful.klas

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StringCoverageTest {

    @Test
    fun `every language the app offers has a string file, and every string file a language`() {
        assertEquals(
            "The `values*/strings.xml` folders and AppLanguage.translated disagree. A folder with no enum " +
                "entry is unreachable — nothing can select it; an entry with no folder claims a language " +
                "and then serves `values/`, which is English. Adding a language is both, plus its plural " +
                "forms in this test's acknowledgement list below.",
            AppLanguage.translated.mapNotNull { it.tag }.sorted(),
            resources.keys.sorted(),
        )
    }

    @Test
    fun `every string the app names exists in every language`() {
        assertEachTranslation(
            "A name exists in one language and not another. `R.string` resolves against `values/`, so this " +
                "does not fail the build — it fails on a phone, in one language only. Add the missing " +
                "entry.",
        ) { tag, translation ->
            (reference.names - translation.names).map { "missing from $tag: $it" } +
                (translation.names - reference.names).map { "in $tag but not in the reference: $it" }
        }
    }

    @Test
    fun `a name is a string everywhere or a plural everywhere`() {
        assertEachTranslation(
            "The same name is a different kind of resource in two languages. `getString` and " +
                "`getQuantityString` are different calls, so whichever one the app makes crashes on one " +
                "of them.",
        ) { tag, translation ->
            translation.names.intersect(reference.names)
                .filter { translation.kindOf(it) != reference.kindOf(it) }
                .map { "$it is ${reference.kindOf(it)} in the reference and ${translation.kindOf(it)} in $tag" }
        }
    }

    @Test
    fun `a string takes the same format arguments in every language`() {
        assertEachTranslation(
            "A string's format arguments differ between two languages. `%1\$s` where another has `%1\$d` " +
                "throws IllegalFormatConversionException the moment the screen draws, in one language " +
                "only; a dropped argument silently loses whatever it was naming.",
        ) { tag, translation ->
            translation.names.intersect(reference.names)
                .filter { translation.argsOf(it) != reference.argsOf(it) }
                .map { "$it: reference takes ${reference.argsOf(it)}, $tag takes ${translation.argsOf(it)}" }
        }
    }

    @Test
    fun `every format argument is positional`() {
        val bare = allResources.flatMap { (tag, resources) ->
            resources.entries
                .filter { (_, text) -> BARE_ARGUMENT.containsMatchIn(text) }
                .map { (name, _) -> "$tag: $name" }
        }

        assertEquals(
            "A string uses a bare `%s`/`%d`. Number them (`%1\$s`) so a translation can reorder the " +
                "sentence around them.",
            emptyList<String>(),
            bare,
        )
    }

    @Test
    fun `each language's plurals carry the forms its own rules select`() {
        val wrong = allResources.flatMap { (tag, resources) ->
            // A language absent from the list is skipped rather than crashing here: that gap is
            // `the plural forms of every language are stated`'s complaint, and it says what to do about it.
            val required = QUANTITIES[tag] ?: return@flatMap emptyList()
            resources.plurals
                .filterValues { !it.containsAll(required) }
                .map { (name, have) -> "$tag: $name has $have, needs $required" }
        }

        assertEquals(
            "A <plurals> is missing a quantity its own language's rules select. The counts that look " +
                "right without the missing form do so by coincidence — Russian's `one`/`few` happen to " +
                "line up with English's `one`/`other` at 1 and 2, which is exactly what made the original " +
                "bug easy to miss.",
            emptyList<String>(),
            wrong,
        )
    }

    @Test
    fun `the plural forms of every language are stated`() {
        assertEquals(
            "A language has no entry in QUANTITIES, so nothing checks that its <plurals> declare the " +
                "forms it selects. Look its CLDR rules up and add them — defaulting to `one`/`other` " +
                "would silently exempt every language that needs more.",
            emptyList<String>(),
            (allResources.keys - QUANTITIES.keys).toList().sorted(),
        )
    }

    @Test
    fun `an indexed string-array is the same length in every language`() {
        assertEachTranslation(
            "A <string-array> has a different number of items in two languages. The arrays read by index " +
                "— a weekday number, a month — fail short with an out-of-bounds read.",
        ) { tag, translation ->
            translation.arrays.keys.intersect(reference.arrays.keys)
                .filter { translation.arrays.getValue(it) != reference.arrays.getValue(it) }
                .map { "$it: ${reference.arrays[it]} items in the reference, ${translation.arrays[it]} in $tag" }
        }
    }

    private fun assertEachTranslation(
        message: String,
        check: (tag: String, translation: Resources) -> List<String>,
    ) {
        val complaints = resources
            .filterKeys { it != AppLanguage.FALLBACK.tag }
            .flatMap { (tag, translation) -> check(tag, translation) }

        assertEquals(message, emptyList<String>(), complaints)
    }

    private val reference: Resources get() = resources.getValue(AppLanguage.FALLBACK.tag!!)

    private val allResources: Map<String, Resources> get() = resources

    private val resources: Map<String, Resources> by lazy {
        val res = ProjectFiles.moduleFile("src/main/res")
        res.listFiles()
            .orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").exists() }
            .mapNotNull { dir ->
                val tag = when {
                    dir.name == "values" -> AppLanguage.FALLBACK.tag
                    dir.name.startsWith("values-") -> dir.name.removePrefix("values-").takeIf {
                        it.length in 2..3 && it.all(Char::isLowerCase)
                    }
                    else -> null
                }
                tag?.let { it to Resources(File(dir, "strings.xml")) }
            }
            .toMap()
    }

    private class Resources(file: File) {
        private val text = file.readText().replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

        /** name → its whole text, for a `<string>` and for a `<plurals>`' items joined. */
        val entries: Map<String, String> = buildMap {
            Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(text)
                .forEach { put(it.groupValues[1], it.groupValues[2]) }
            PLURALS.findAll(text).forEach { put(it.groupValues[1], it.groupValues[2]) }
        }

        /** name → the quantities its items declare, for `<plurals>` only. */
        val plurals: Map<String, Set<String>> = PLURALS.findAll(text).associate { match ->
            match.groupValues[1] to Regex("""quantity="([^"]+)"""")
                .findAll(match.groupValues[2])
                .map { it.groupValues[1] }
                .toSet()
        }

        /** name → how many `<item>`s it holds, for `<string-array>` only. */
        val arrays: Map<String, Int> =
            Regex("""<string-array\s+name="([^"]+)"[^>]*>(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(text)
                .associate { it.groupValues[1] to Regex("""<item>""").findAll(it.groupValues[2]).count() }

        /** Every name declared, arrays included: all of them are things `R` can be asked for. */
        val names: Set<String> get() = entries.keys + arrays.keys

        fun kindOf(name: String): String = if (name in plurals) "a <plurals>" else "a <string>"

        /**
         * The positional arguments a string takes, as `1$s`, `2$d` — the *set*, not the order, because a
         * translation is allowed to reorder them and that is the point of numbering them.
         */
        fun argsOf(name: String): Set<String> =
            POSITIONAL_ARGUMENT.findAll(entries[name] ?: "").map { it.groupValues[1] }.toSet()

        companion object {
            private val PLURALS =
                Regex("""<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        }
    }

    companion object {

        private val QUANTITIES: Map<String, Set<String>> = mapOf(
            "en" to setOf("one", "other"),
            "ru" to setOf("one", "few", "many", "other"),
        )

        private val POSITIONAL_ARGUMENT = Regex("""%(\d+\$[a-zA-Z])""")

        private val BARE_ARGUMENT = Regex("""(?<!%)%[-+ 0-9.]*[sdf]""")
    }
}
