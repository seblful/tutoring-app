package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A section title is a [SectionTitle]** (docs/ui-conventions.md → *Section titles and dialogs*).
 *
 * The rule was written down and then spelled out by hand at every use, and it drifted the way
 * hand-spelled rules do: the lesson sheet titled «Статус» in grey `labelMedium`, the booking form one tap
 * further titled «Длительность» in `labelLarge` and «Участники» in `titleSmall`, and only some of them
 * were headings a screen reader could jump to. A `Text` given both `titleSmall` and `heading()` is the
 * hand-spelled form; comments are stripped first (see [SourceText.code]), so a KDoc may still name it.
 */
class SectionTitleSurfaceTest {

    private fun uiSources() = ProjectFiles.sources().filter { "ui" in it.path.split(java.io.File.separator) }

    @Test
    fun `no screen spells a section title out by hand`() {
        val offenders = uiSources()
            .filterNot { it.name == "Surfaces.kt" }
            .filter { file ->
                SourceText.argumentLists(SourceText.code(file.readText()), "Text(").any { args ->
                    "titleSmall" in args && "heading()" in args
                }
            }
            .map { it.name }

        assertEquals(
            "A section title is drawn as a `Text` styled `titleSmall` with heading semantics, instead of " +
                "the shared `SectionTitle` in ui/common/Surfaces.kt: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /** And the screens with sections do use it, so the test above cannot pass by there being none left. */
    @Test
    fun `the forms, the sheet, the cards and settings title their sections with it`() {
        val users = uiSources()
            .filter { SourceText.code(it.readText()).contains("SectionTitle(") }
            .map { it.name }
            .toSet()

        assertTrue(
            "Expected the forms, the lesson sheet, the report cards and settings to use `SectionTitle`: $users",
            users.containsAll(
                setOf(
                    "ClassEditScreen.kt",
                    "StudentFormScreen.kt",
                    "GroupFormScreen.kt",
                    // The lesson sheet, and settings' section headers.
                    "ClassActionsSheet.kt",
                    "ReportsScreen.kt",
                    "SettingsRows.kt",
                    "HomeworkParts.kt",
                ),
            ),
        )
    }
}
