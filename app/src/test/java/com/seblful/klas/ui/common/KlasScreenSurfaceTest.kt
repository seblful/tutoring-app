package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The screen shell is written down once**, in `KlasScreen.kt`.
 *
 * Fifteen screens each spelled out their own `Scaffold` + `TopAppBar` + back arrow + `SnackbarHost` +
 * `nestedScroll` + `constrainedContent()`. None of them was wrong. What was missing was anything making
 * a *new* screen match — and two of the fifteen had already drifted: `MoreScreen` and `BackupScreen`
 * carried no scroll behaviour at all, so their bars never took on the tonal change that separates a bar
 * from the page under it, on the only two screens where nobody noticed.
 *
 * The same argument as `ScreenStateSurfaceTest`, one layer up: a shell is the kind of thing that gets
 * pasted rather than called, and `KlasScreen` existing does not stop the long form being written out
 * again. This is what makes writing it out a failure instead of a review note.
 */
class KlasScreenSurfaceTest {

    private fun uiSources() = ProjectFiles.sources()
        .filter { "ui" in it.path.split(java.io.File.separator) }

    /**
     * The file's **code**, with comments stripped.
     *
     * This test scans source text, and the things it scans for are exactly the things the KDoc next to
     * them has to be able to name: `KlasScreen`'s own doc explains what it replaced, and every screen
     * that used to carry a `Scaffold` says so in a comment. Matching raw text would make writing that
     * explanation fail the build — a tripwire that punishes documenting the rule it enforces.
     */
    private fun code(file: java.io.File): String = file.readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    /**
     * Matches `Scaffold(` as a *call* — so `NavigationSuiteScaffold(` in `AppRoot.kt` does not count.
     * That one is the root of the tree and deliberately the only other scaffold in the app: there is
     * exactly one per tree, and nesting a second applied the status-bar inset twice.
     */
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

    /** And it really is used, so the test above cannot pass by there being no screens left. */
    @Test
    fun `the screens do take their shell from it`() {
        val users = uiSources().count { code(it).contains("KlasScreen(") }

        assertTrue("Nothing calls `KlasScreen` — has the shell moved?", users >= 10)
    }

    /**
     * The back arrow was written out eight times. It is one glyph, one content description and one
     * `IconButton`, and the only reason to write it again is to put it somewhere a `KlasScreen` cannot —
     * which is the change this test wants to be told about.
     */
    @Test
    fun `only KlasScreen draws the back arrow`() {
        val offenders = uiSources()
            .filterNot { it.name == "KlasScreen.kt" }
            .filter { code(it).contains("ArrowBack") }
            .map { it.name }

        assertEquals(
            "A screen draws its own back arrow. `KlasScreen(onBack = …)` owns it — a form passes its " +
                "`leaveGuard` there, not a bare `onBack`: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * `SnackbarHost` belongs to the shell for the same reason `SnackbarHostState` belongs to `Messages`
     * (`ScreenStateSurfaceTest`): a screen that mounts its own host can mount it in the wrong place —
     * over the bottom bar, or outside the width cap — and nothing above it would say so.
     */
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
