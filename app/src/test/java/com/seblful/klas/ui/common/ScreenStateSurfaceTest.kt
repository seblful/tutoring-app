package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The sharing policy is written down once**, in `ScreenState.kt`, and the snackbar is reached through
 * `Messages`.
 *
 * Twenty-six flows ended with the same three lines, and the `5_000` grace period appeared twenty-six
 * times — a number nobody could change without finding all of them, in a policy that decides whether
 * rotating the device recomputes every class against every payment. Eleven screens ended with the same
 * six-line snackbar collector, and three of them had quietly drifted onto a raw `SnackbarHostState`,
 * losing the "dismiss what is already up" rule that keeps a second failure from arriving after the tutor
 * has moved on.
 *
 * Both are the kind of tail that gets pasted rather than called, which is why a test says so rather than
 * a comment. Neither `derive`/`hold` nor `ShowMessagesFor` prevents the long form from being written out
 * again; this is what makes writing it out a failure instead of a review note.
 */
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

    /** And they really are used, so the test above cannot pass by there being no derived state left. */
    @Test
    fun `the screens do derive their state through it`() {
        val users = uiSources().count { it.readText().contains(".derive(this,") }

        assertTrue("Nothing calls `derive` — has the helper moved?", users >= 10)
    }

    /**
     * A raw `SnackbarHostState` is how the three drifted screens bypassed [Messages]. `rememberMessages`
     * is the one place allowed to build one.
     */
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
