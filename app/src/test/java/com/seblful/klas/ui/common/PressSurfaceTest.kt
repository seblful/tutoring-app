package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressSurfaceTest {

    private fun uiSources() = ProjectFiles.sources().filter { "ui" in it.path.split(java.io.File.separator) }

    @Test
    fun `a tappable list row presses as a rounded shape`() {
        val offenders = uiSources().filter { file ->
            SourceText.argumentLists(SourceText.code(file.readText()), "ListItem(").any { args ->
                PressModifiers.any { it in args } && "RowPress" !in args
            }
        }.map { it.name }

        assertEquals(
            "A `ListItem` takes a press without `insetRowPress`, so holding it lights a full-width grey " +
                "band instead of the rounded shape every other row presses with: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `a bottom sheet draws its own handle`() {
        val offenders = uiSources().filter { file ->
            SourceText.argumentLists(SourceText.code(file.readText()), "ModalBottomSheet(").any { args ->
                "dragHandle = null" !in args
            }
        }.map { it.name }

        assertEquals(
            "A sheet leaves Material its drag handle, which it wraps in a clickable, tooltipped box — " +
                "held, a grey square and an English «Drag handle». Pass `dragHandle = null` and draw " +
                "`SheetHandle()` as the sheet's first row: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the menus, settings and cards press their rows through it`() {
        val users = uiSources().filter { SourceText.code(it.readText()).contains("RowPress {") }.map { it.name }.toSet()

        assertTrue(
            "Expected «Ещё», settings and the cards to press their rows through `insetRowPress`: $users",
            users.containsAll(
                setOf("MoreScreen.kt", "SettingsScreen.kt", "StudentDetailScreen.kt", "GroupDetailScreen.kt"),
            ),
        )
    }

    private companion object {
        val PressModifiers = listOf("clickable", "toggleable(", "selectable(")
    }
}
