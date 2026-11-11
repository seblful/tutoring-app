package com.seblful.klas

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The component catalog (debug sources) shows every design-system part, so it stays the place to look first. */
class ComponentCatalogTest {

    @Test
    fun `every public design-system composable is in the catalog`() {
        val components = ProjectFiles.sourceFile("ui/designsystem/component")
            .listFiles { file -> file.extension == "kt" }.orEmpty()
            .flatMap { file -> Composable.findAll(file.readText()).map { it.groupValues[1] }.toList() }
        assertTrue("no design-system parts found", components.isNotEmpty())
        val catalog = ProjectFiles.moduleFile("src/debug/java/com/seblful/klas/preview/ComponentCatalog.kt").readText()

        val missing = components.filter { it !in OwnWindows && !Regex("""\b$it\b""").containsMatchIn(catalog) }

        assertEquals("parts the catalog leaves out", emptyList<String>(), missing)
    }

    private companion object {
        /** A top-level `@Composable fun Name(` that is public or internal — a part features build with. */
        val Composable = Regex("""@Composable\s+(?:internal\s+)?fun ([A-Z]\w*)\(""")

        /** Dialogs, the sheet and a tip's bubble open windows of their own; a static page cannot show them. */
        val OwnWindows = setOf("FormDialog", "ChoiceDialog", "KlasSheet", "CoachMark")
    }
}
