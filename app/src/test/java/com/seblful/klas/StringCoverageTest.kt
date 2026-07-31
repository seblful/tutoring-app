package com.seblful.klas

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **Every language the app claims describes the same app.**
 *
 * A missing translation is not a compile error and not a lint error a build fails on: `R.string.foo` resolves
 * against `values/`, so the app builds, ships, and then throws `Resources.NotFoundException` — or silently
 * draws the wrong language — for whichever tutors read the other folder. That failure is invisible on the
 * developer's own phone if their phone happens to be set to the language they added the string to, which is
 * the whole reason this is a test and not a habit.
 *
 * **It discovers the folders rather than naming them**, and that is the part that makes adding a third
 * language a bounded job instead of a guess. Every `strings.xml` under a `values…` folder in `res/` is
 * found, `values/` is the reference (English, and the fallback Android loads for an unmatched locale), and
 * each of the others is compared against it. Dropping a `values-de/` into the tree puts German under every
 * assertion
 * below at once — where the earlier version of this test named its two files and would have gone on passing
 * while German drifted.
 *
 * It reads the XML as text rather than through `R`, for the reason `MigrationChainTest` reads
 * `AppDatabase.kt`: a unit test has no resource table, and going through `R` would only ever ask about the
 * folder the JVM build picked.
 *
 * What it checks, in the order these actually go wrong:
 *
 * 1. **The folders and `AppLanguage` agree.** A folder with no enum entry is unreachable; an enum entry with
 *    no folder claims a language and serves English.
 * 2. **Every name exists in every language.** Adding a string to one folder and forgetting the others.
 * 3. **A name is the same *kind* everywhere** — a `<string>` here and a `<plurals>` there is a crash,
 *    because `getString` and `getQuantityString` are different calls.
 * 4. **Format arguments match.** `%1$s` in one language and `%1$d` in another is an
 *    `IllegalFormatConversionException` at the moment the screen draws, in one language only. Positional
 *    every time (`%1$s`, never `%s`), since a translation reorders a sentence and an unpositioned argument
 *    cannot be moved with it.
 * 5. **Each language declares the plural forms its own rules select** — the rule set comes from the
 *    *configuration's* locale rather than from the folder the string was loaded from, so a Russian plural
 *    missing `many` says «5 занятия».
 * 6. **A `<string-array>` has the same length everywhere.** Both of the app's arrays are indexed by
 *    something — a weekday number, a month — so a short one is an out-of-bounds read.
 */
class StringCoverageTest {

    @Test
    fun `every language the app offers has a string file, and every string file a language`() {
        assertEquals(
            "The `values*/strings.xml` folders and AppLanguage.translated disagree. A folder with no enum " +
                "entry is unreachable — nothing can select it; an entry with no folder claims a language " +
                "and then serves `values/`, which is English. Adding a language is both, plus its plural " +
                "forms in this test's acknowledgement list below.",
            // `mapNotNull` only to drop the nullability: a translated language has a tag by definition,
            // which `AppLanguage.translated` is the statement of.
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

    /**
     * Positional and not bare, everywhere. A translation reorders a sentence — «Долг 240» against "Owes 240"
     * is the easy case, `%1$s → %2$s` against a language that puts the new rate first is not — and a bare
     * `%s` cannot be moved. Android lint's own check for this only fires where a string has two or more.
     */
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

    /**
     * Each language's own quantity forms, from the acknowledgement list below.
     *
     * The forms a language selects are CLDR's and not something a JVM test can look up, so they are stated —
     * which is the point: a new `values-xx/` fails [`the plural forms of every language are stated`] until
     * somebody has looked its rules up, rather than passing on English's two by default.
     */
    @Test
    fun `each language's plurals carry the forms its own rules select`() {
        val wrong = allResources.flatMap { (tag, resources) ->
            // A language absent from the list is skipped rather than crashing here: that gap is
            // `the plural forms of every language are stated`'s complaint, and it says what to do about it.
            // Both failing at once would bury the useful message under this one's stack trace.
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

    /** And the list itself has to cover every language, or the check above would skip one. */
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
            "A <string-array> has a different number of items in two languages. Both of the app's arrays " +
                "are read by index — a weekday number, a month — so the short one is an out-of-bounds read.",
        ) { tag, translation ->
            translation.arrays.keys.intersect(reference.arrays.keys)
                .filter { translation.arrays.getValue(it) != reference.arrays.getValue(it) }
                .map { "$it: ${reference.arrays[it]} items in the reference, ${translation.arrays[it]} in $tag" }
        }
    }

    // ---- the folders, and how each is compared with the reference ---------------------------------

    /** Every complaint [check] makes about every translation, as one assertion. */
    private fun assertEachTranslation(
        message: String,
        check: (tag: String, translation: Resources) -> List<String>,
    ) {
        val complaints = resources
            .filterKeys { it != AppLanguage.FALLBACK.tag }
            .flatMap { (tag, translation) -> check(tag, translation) }

        assertEquals(message, emptyList<String>(), complaints)
    }

    /** The reference every translation is compared against: `values/`, which is English. */
    private val reference: Resources get() = resources.getValue(AppLanguage.FALLBACK.tag!!)

    private val allResources: Map<String, Resources> get() = resources

    /**
     * Language tag → its strings, discovered from the tree.
     *
     * `values/` is keyed by [AppLanguage.FALLBACK]'s tag rather than by `""`, because that is what the folder
     * *means*: Android loads it for an unmatched locale, so the default folder and the fallback language are
     * the same statement made in two places. Only single-language qualifiers are read (`values-ru`, not
     * `values-ru-rBY` or `values-night`); a region-qualified string folder would be a new thing to think
     * about and this would then fail the folders-and-enum check rather than quietly half-covering it.
     */
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

    /**
     * One folder's `strings.xml`, parsed just far enough for the questions above.
     *
     * With regexes rather than an XML parser, deliberately: what is being asserted is a property of the
     * *file* — which names it declares, and what they say — and a parser would need the whole resource
     * pipeline to say anything more than this does. Comments are stripped first, since the files are heavily
     * commented and a `%s` inside a note about `%s` is not a format argument.
     */
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
        /**
         * **The acknowledgement list**: the plural forms each language the app ships selects, per CLDR.
         *
         * Stated rather than derived, because CLDR's rules are not reachable from a JVM unit test — and
         * because stating them is what makes adding a language a deliberate act. A new folder fails
         * `the plural forms of every language are stated` until it appears here, which is the moment to look
         * the rules up rather than assume English's two.
         *
         * Only the forms that must be *present* are listed. A language may declare more (`zero`, `two`) than
         * its selector will ever pick, which is harmless.
         */
        private val QUANTITIES: Map<String, Set<String>> = mapOf(
            "en" to setOf("one", "other"),
            "ru" to setOf("one", "few", "many", "other"),
        )

        /** `%1$s`, `%2$d` — the number and the conversion, which both have to match across languages. */
        private val POSITIONAL_ARGUMENT = Regex("""%(\d+\$[a-zA-Z])""")

        /**
         * A `%s` or `%d` with no position. `%%` is an escaped percent sign and not an argument — the
         * reports print «%» that way — so it is excluded rather than matched and filtered.
         */
        private val BARE_ARGUMENT = Regex("""(?<!%)%[-+ 0-9.]*[sdf]""")
    }
}
