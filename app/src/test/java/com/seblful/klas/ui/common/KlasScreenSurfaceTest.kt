package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KlasScreenSurfaceTest {

    private fun uiSources() = ProjectFiles.sources()
        .filter { "ui" in it.path.split(java.io.File.separator) }

    private fun code(file: java.io.File): String = SourceText.code(file.readText())

    private val scaffoldCall = Regex("""(^|[^A-Za-z])Scaffold\(""")

    @Test
    fun `only KlasScreen builds a Scaffold`() {
        val offenders = uiSources()
            .filterNot { it.name == "KlasScreen.kt" }
            .filter { scaffoldCall.containsMatchIn(code(it)) }
            .map { it.name }

        assertEquals(
            "A screen builds its own Scaffold instead of using `KlasScreen`. The insets, the width " +
                "cap, the `imePadding()`-before-the-scroll rule and the scroll behaviour all live in " +
                "`ui/common/KlasScreen.kt`, so a screen that scaffolds itself is a second shell: " +
                "$offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the screens do take their shell from it`() {
        val users = uiSources().count { code(it).contains("KlasScreen(") }

        assertTrue("Nothing calls `KlasScreen` — has the shell moved?", users >= 10)
    }

    @Test
    fun `only KlasScreen draws the back arrow`() {
        val offenders = uiSources()
            .filterNot { it.name == "KlasScreen.kt" }
            .filter { code(it).contains("KlasIcons.Filled.ArrowBack") }
            .map { it.name }

        assertEquals(
            "A screen draws its own back arrow. `KlasScreen(onBack = …)` owns it — a form passes its " +
                "`leaveGuard` there, not a bare `onBack`: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `only KlasScreen mounts the snackbar host`() {
        val offenders = uiSources()
            .filterNot { it.name == "KlasScreen.kt" }
            .filter { code(it).contains("SnackbarHost(") }
            .map { it.name }

        assertEquals(
            "A screen mounts its own `SnackbarHost`. Pass `messages` to `KlasScreen` instead: $offenders",
            emptyList<String>(),
            offenders,
        )
    }
}
