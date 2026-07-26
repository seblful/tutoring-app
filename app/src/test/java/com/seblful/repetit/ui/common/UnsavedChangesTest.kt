package com.seblful.repetit.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What back does on a form. The rule exists because the wrong answer is unrecoverable: a filled-in
 * form used to be thrown away silently.
 */
class UnsavedChangesTest {

    @Test
    fun `an untouched form leaves without asking`() {
        assertEquals(LeaveIntent.LEAVE, leaveIntent(dirty = false, canSave = false))
        // Valid but untouched — an existing record opened and closed again. Nothing to ask about.
        assertEquals(LeaveIntent.LEAVE, leaveIntent(dirty = false, canSave = true))
    }

    @Test
    fun `a complete form offers to save it`() {
        assertEquals(LeaveIntent.ASK_SAVE_OR_DISCARD, leaveIntent(dirty = true, canSave = true))
    }

    /**
     * Every form's save is a no-op while it is invalid, so offering «Сохранить» there would be an
     * offer the app then declines — the tutor would tap it and watch nothing happen.
     */
    @Test
    fun `an incomplete form only asks about losing the work`() {
        assertEquals(LeaveIntent.ASK_DISCARD, leaveIntent(dirty = true, canSave = false))
    }
}
