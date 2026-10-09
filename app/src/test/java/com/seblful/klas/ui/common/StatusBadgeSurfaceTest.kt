package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A lesson's state is a [StatusBadge], never a font style** (docs/ui-conventions.md → Lists).
 *
 * The schedule said «Проведено» in a pill, and so did the student card once it was brought into line —
 * but the group card's lessons still said it as green text, so the same lesson read two ways depending on
 * which card it was opened from. Each card was fixed on its own, which is how the third was missed: the
 * rule lived in a comment beside the fix rather than anywhere that fails.
 *
 * The status words are the two `@Composable`s in `DomainText.kt`; handing either to a bare `Text` is the
 * font-style form. Comments are stripped first (see [SourceText.code]), so a KDoc may still name the rule.
 */
class StatusBadgeSurfaceTest {

    private fun uiSources() = ProjectFiles.sources().filter { "ui" in it.path.split(java.io.File.separator) }

    @Test
    fun `a lesson's status is never drawn as plain text`() {
        val offenders = uiSources().filter { file ->
            SourceText.argumentLists(SourceText.code(file.readText()), "Text(").any { args ->
                StatusWords.any { it in args }
            }
        }.map { it.name }

        assertEquals(
            "A lesson's status is drawn as a `Text` — a font style — instead of in a `StatusBadge`, the pill " +
                "every other screen says it in: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /** And the badges really do carry it, so the test above cannot pass by there being no status left. */
    @Test
    fun `the cards and the schedule say it in a badge`() {
        val users = uiSources().filter { file ->
            SourceText.argumentLists(SourceText.code(file.readText()), "StatusBadge(").any { args ->
                StatusWords.any { it in args }
            }
        }.map { it.name }.toSet()

        assertTrue(
            "Expected the schedule, the student card and the group card to badge a lesson's status: $users",
            users.containsAll(setOf("LessonCard.kt", "StudentDetailScreen.kt", "GroupDetailScreen.kt")),
        )
    }

    private companion object {
        val StatusWords = listOf("classStatusLabel(", "lessonBadgeLabel(")
    }
}
