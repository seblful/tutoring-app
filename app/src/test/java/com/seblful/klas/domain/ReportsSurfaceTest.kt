package com.seblful.klas.domain

import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsSurfaceTest {

    private val reference = Regex("""\bReports\.([A-Za-z_][A-Za-z0-9_]*)""")

    private fun code(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `nothing outside Reports assembles a report itself`() {
        val callers = ProjectFiles.sources()
            .filterNot { it.name == "Reports.kt" }
            .flatMap { file ->
                reference.findAll(code(file.readText()))
                    .map { it.groupValues[1] }
                    // `window` says which classes to read for `of`, not what a report contains.
                    .filter { it.first().isLowerCase() && it !in setOf("of", "window") }
                    .map { "${file.name} → Reports.$it" }
            }
            .distinct()

        assertEquals(
            "The app reaches past `Reports.of` for a piece of a report. Whatever it does with that " +
                "piece — filter it, sort it, decide it is not worth showing — is a rule about reports, " +
                "and it belongs in `of` where the report is tested: $callers",
            emptyList<String>(),
            callers,
        )
    }

    @Test
    fun `something outside Reports does call of`() {
        val callers = ProjectFiles.sources()
            .filterNot { it.name == "Reports.kt" }
            .filter { code(it.readText()).contains("Reports.of(") }
            .map { it.name }

        assertTrue("Nothing calls `Reports.of` — has the entry point moved?", callers.isNotEmpty())
    }
}
