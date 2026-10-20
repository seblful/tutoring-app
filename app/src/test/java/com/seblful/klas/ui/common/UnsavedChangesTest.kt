package com.seblful.klas.ui.common

import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Test

class UnsavedChangesTest {

    private class Form(
        override val isDirty: Boolean,
        override val canSave: Boolean,
    ) : LeavableForm

    private fun intentFor(dirty: Boolean, canSave: Boolean) = leaveIntent(Form(dirty, canSave))

    @Test
    fun `an untouched form leaves without asking`() {
        assertEquals(LeaveIntent.LEAVE, intentFor(dirty = false, canSave = false))
        assertEquals(LeaveIntent.LEAVE, intentFor(dirty = false, canSave = true))
    }

    @Test
    fun `a complete form offers to save it`() {
        assertEquals(LeaveIntent.ASK_SAVE_OR_DISCARD, intentFor(dirty = true, canSave = true))
    }

    @Test
    fun `an incomplete form only asks about losing the work`() {
        assertEquals(LeaveIntent.ASK_DISCARD, intentFor(dirty = true, canSave = false))
    }

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
