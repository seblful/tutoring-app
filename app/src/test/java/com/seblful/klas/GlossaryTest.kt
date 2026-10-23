package com.seblful.klas

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The words the app uses for one thing, held to one each — the glossary in `docs/ui-conventions.md`. A
 * retired word coming back in a new string fails here, with the word to use instead.
 */
class GlossaryTest {

    @Test
    fun `no string uses a word the glossary retired`() {
        val found = RETIRED.flatMap { (folder, words) ->
            val strings = stringsIn(folder)
            words.flatMap { word ->
                strings.filter { (name, text) -> word.applies(name) && word.pattern.containsMatchIn(text) }
                    .map { (name, text) -> "$folder/$name: «$text» — ${word.use}" }
            }
        }

        assertEquals("A string uses a retired word. Use the glossary's instead.", emptyList<String>(), found)
    }

    private class Retired(regex: String, val use: String, private val only: String? = null) {
        val pattern = Regex(regex, setOf(RegexOption.IGNORE_CASE))

        /** Whether this word is checked in [name] — some are retired for one feature's strings only. */
        fun applies(name: String) = only == null || name.startsWith(only)
    }

    private fun stringsIn(folder: String): Map<String, String> {
        val text = File(ProjectFiles.moduleFile("src/main/res"), "$folder/strings.xml").readText()
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        return buildMap {
            Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(text).forEach { put(it.groupValues[1], it.groupValues[2]) }
            Regex("""<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(text).forEach { put(it.groupValues[1], it.groupValues[2]) }
        }
    }

    private companion object {
        // `(?U)` makes `\b` see Cyrillic letters as word letters.
        val RETIRED: Map<String, List<Retired>> = mapOf(
            "values" to listOf(
                Retired("""\bpric(e|es|ed|ing)\b""", "say «rate»"),
                Retired("""\bcomplet(e|ed|es|ion)\b|auto-?completion""", "say «taught» / «mark taught»"),
                Retired("""open the (student's |student\\'s )?card|\bopen student\b""", "say «Open profile»"),
                Retired("""\bpicked:|\bnot picked\b""", "say «No subject», or put the count in the header"),
                Retired("""\bbring back\b""", "say «Restore»"),
                Retired("""\bshow archive\b""", "say «Show archived»"),
                Retired("""\bdata saved\b""", "say what was saved: «Backup saved», «Report saved»"),
                Retired("""\barchive\b""", "finished homework is «Done»", only = "homework_"),
            ),
            "values-ru" to listOf(
                Retired("""(?U)\bцен(а|ы|у|е|ой|ами|ах)?\b""", "говорите «ставка»"),
                Retired("""(?U)автозаверш""", "говорите «автоотметка» / «отмечено проведённым»"),
                Retired("""(?U)открыть карточку""", "говорите «Открыть профиль»"),
                Retired("""(?U)\bне выбран\b""", "говорите «Без предмета»"),
                Retired("""(?U)\bархив""", "сделанные задания — «Сделанные»", only = "homework_"),
            ),
        )
    }
}
