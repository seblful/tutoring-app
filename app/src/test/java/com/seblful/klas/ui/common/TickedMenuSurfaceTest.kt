package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.ui.common.state.map
import com.seblful.klas.ui.theme.KlasIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Selection is `selected` semantics, not a tick alone** (docs/ui-conventions.md → Screen readers).
 *
 * Four menus ticked the entry in force and two of them stopped at the picture: «Сортировка» and the
 * report's period read every entry the same way to a screen reader. [TickedMenuItem] carries both halves,
 * so a menu entry that draws the tick itself — `KlasIcons.Filled.Check` handed to a `DropdownMenuItem` — is the
 * one that can leave the second half out.
 */
class TickedMenuSurfaceTest {

    private fun uiSources() = ProjectFiles.sources().filter { "ui" in it.path.split(java.io.File.separator) }

    @Test
    fun `no menu entry draws its own tick`() {
        val offenders = uiSources()
            .filterNot { it.name == "Menus.kt" }
            .filter { file ->
                SourceText.argumentLists(SourceText.code(file.readText()), "DropdownMenuItem(").any { args ->
                    "KlasIcons.Filled.Check" in args
                }
            }
            .map { it.name }
        assertEquals(
            "A menu entry is ticked by hand, so nothing says «выбрано» about it to a screen reader. Use " +
                "`TickedMenuItem` (ui/common/Menus.kt): $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /** And the ticked menus do use it, so the test above cannot pass by there being none left. */
    @Test
    fun `the sort, the period, the filters and the card menus tick through it`() {
        val users = uiSources()
            .filter { SourceText.code(it.readText()).contains("TickedMenuItem(") }
            .map { it.name }
            .toSet()
        assertTrue(
            "Expected the people sort, the report's period, the filter menu and the card view menu to use " +
                "`TickedMenuItem`: $users",
            users.containsAll(setOf("PeopleScreen.kt", "ReportsScreen.kt", "Filters.kt", "StickyViewMenu.kt")),
        )
    }
}
