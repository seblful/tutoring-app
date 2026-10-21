package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
                    "ClassEditSections.kt",
                    "StudentFormScreen.kt",
                    "GroupFormScreen.kt",
                    "ClassActionsSheet.kt",
                    "ReportsScreen.kt",
                    "SettingsRows.kt",
                    "HomeworkParts.kt",
                ),
            ),
        )
    }
}
