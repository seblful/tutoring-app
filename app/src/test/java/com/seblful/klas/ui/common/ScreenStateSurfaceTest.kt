package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateSurfaceTest {

    private fun uiSources() = ProjectFiles.sources().filter { "ui" in it.path.split(java.io.File.separator) }

    @Test
    fun `only ScreenState names the sharing policy`() {
        val offenders = uiSources()
            .filterNot { it.name == "ScreenState.kt" }
            .filter { file ->
                val code = file.readText()
                code.contains("SharingStarted.") || code.contains(".stateIn(")
            }
            .map { it.name }

        assertEquals(
            "A flow spells its own `stateIn`/`SharingStarted` instead of ending in `derive` or `hold`. " +
                "The grace period and the sharing rule live in `ui/common/state/ScreenState.kt`, so a flow " +
                "that states them again is a second policy: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the screens do derive their state through it`() {
        val users = uiSources().count { it.readText().contains(".derive(this,") }

        assertTrue("Nothing calls `derive` — has the helper moved?", users >= 10)
    }

    @Test
    fun `only Messages holds a SnackbarHostState`() {
        val offenders = uiSources()
            .filterNot { it.name == "Messages.kt" }
            .filter { it.readText().contains("SnackbarHostState()") }
            .map { it.name }

        assertEquals(
            "A screen builds its own SnackbarHostState, so it loses the dismiss-what-is-up rule that " +
                "keeps a transient message from arriving late. Use `rememberMessages()`: $offenders",
            emptyList<String>(),
            offenders,
        )
    }
}
