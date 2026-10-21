package com.seblful.klas.ui.common

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The system back gesture, not the toolbar arrow, through [leaveGuard]. */
@RunWith(RobolectricTestRunner::class)
class LeaveGuardBackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Form(override val isDirty: Boolean, override val canSave: Boolean) : LeavableForm

    private var leaves = 0

    private fun show(form: Form) = compose.setContent {
        KlasTheme { leaveGuard(form, onLeave = { leaves++ }, onSave = {}) }
    }

    private fun systemBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `system back on a typed form asks instead of leaving`() {
        show(Form(isDirty = true, canSave = true))

        systemBack()

        assertEquals(0, leaves)
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithText(compose.activity.getString(R.string.unsaved_save_title)).assertExists()
    }

    @Test
    fun `system back on an untouched form is not intercepted`() {
        show(Form(isDirty = false, canSave = true))

        systemBack()

        // Nothing to ask about, so the gesture falls through to the host — here, finishing the activity.
        assertEquals(0, leaves)
        assertTrue(compose.activity.isFinishing)
    }
}
