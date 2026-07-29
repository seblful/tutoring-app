package com.seblful.klas.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What back does on a form. The rule exists because the wrong answer is unrecoverable: a filled-in
 * form used to be thrown away silently.
 */
class UnsavedChangesTest {

    /** The two facts a form has to be able to state about itself, and nothing else. */
    private class Form(
        override val isDirty: Boolean,
        override val canSave: Boolean,
    ) : LeavableForm

    private fun intentFor(dirty: Boolean, canSave: Boolean) = leaveIntent(Form(dirty, canSave))

    @Test
    fun `an untouched form leaves without asking`() {
        assertEquals(LeaveIntent.LEAVE, intentFor(dirty = false, canSave = false))
        // Valid but untouched — an existing record opened and closed again. Nothing to ask about.
        assertEquals(LeaveIntent.LEAVE, intentFor(dirty = false, canSave = true))
    }

    @Test
    fun `a complete form offers to save it`() {
        assertEquals(LeaveIntent.ASK_SAVE_OR_DISCARD, intentFor(dirty = true, canSave = true))
    }

    /**
     * Every form's save is a no-op while it is invalid, so offering «Сохранить» there would be an
     * offer the app then declines — the tutor would tap it and watch nothing happen.
     */
    @Test
    fun `an incomplete form only asks about losing the work`() {
        assertEquals(LeaveIntent.ASK_DISCARD, intentFor(dirty = true, canSave = false))
    }

    /**
     * The wiring around the question is `leaveGuard`'s, once. Three screens used to spell it by
     * hand, and two of them answered «Сохранить» without putting the dialog away — so a refused
     * save left the dialog standing over its own snackbar, offering only the answers that discard
     * the work. A screen asking [leaveIntent] itself is a screen about to re-make that wiring.
     */
    @Test
    fun `only the guard asks the leave question`() {
        val offenders = com.seblful.klas.testutil.ProjectFiles.sources()
            .filter { "ui" in it.path.split(java.io.File.separator) }
            .filterNot { it.name == "UnsavedChanges.kt" }
            .filter { it.readText().contains("leaveIntent(") }
            .map { it.name }

        assertEquals(
            "A screen calls `leaveIntent` directly instead of going through `leaveGuard`, which is " +
                "how the three copies drifted apart the first time: $offenders",
            emptyList<String>(),
            offenders,
        )
    }
}
