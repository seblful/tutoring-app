package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
