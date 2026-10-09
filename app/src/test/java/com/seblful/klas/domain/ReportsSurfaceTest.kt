package com.seblful.klas.domain

import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The app asks [Reports] one question.** `Reports.of` returns the whole report; the aggregations are
 * its parts.
 *
 * All ten of them used to be called from one file, and the calling was the problem rather than the
 * count: three handed back raw maps whose filter and order the view model restated, "a single kind is
 * not a section" lived at the call site, and a composable re-derived the trend's rows next to a comment
 * explaining why. Every one of those is a statement about what a report *is*, made somewhere a report
 * is not tested.
 *
 * The parts stay public and stay tested one rule at a time — `percentOf`'s rounding and `change`'s
 * refusal to call growth from zero a percentage are much better read as their own tests than through an
 * assembled report. What this stops is the app reaching past `of` to reassemble one, which is how the
 * decisions leaked out in the first place. A type is fine: the screen draws `Reports.Trend` and
 * `Reports.Line`.
 */
class ReportsSurfaceTest {

    /** `Reports.<something>`, capturing the first name after the dot. */
    private val reference = Regex("""\bReports\.([A-Za-z_][A-Za-z0-9_]*)""")

    /**
     * Comments and KDoc removed, because a `[Reports.percentOf]` link is a cross-reference and not a
     * call — `model/Ids.kt` and `Money.kt` both explain themselves by naming the aggregation they exist
     * for, which is exactly the kind of pointer that should stay.
     */
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
                    // A capitalised name is one of the types — a report, a line, a trend to draw.
                    .filter { it.first().isLowerCase() && it != "of" }
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

    /**
     * And the entry point is really used, so the test above cannot pass by the screen having stopped
     * reading reports at all.
     */
    @Test
    fun `something outside Reports does call of`() {
        val callers = ProjectFiles.sources()
            .filterNot { it.name == "Reports.kt" }
            .filter { code(it.readText()).contains("Reports.of(") }
            .map { it.name }

        assertTrue("Nothing calls `Reports.of` — has the entry point moved?", callers.isNotEmpty())
    }
}
