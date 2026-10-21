package com.seblful.klas

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PermissionPromptTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calendar = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private fun prompt(): PermissionPrompt {
        lateinit var prompt: PermissionPrompt
        compose.setContent { prompt = rememberPermissionPrompt(calendar) {} }
        compose.waitForIdle()
        return prompt
    }

    @Test
    fun `nothing is explained before the first ask`() {
        assertFalse(prompt().rationaleDue())
    }

    @Test
    fun `a refusal of any one permission is explained before the next ask`() {
        val prompt = prompt()
        shadowOf(compose.activity.packageManager)
            .setShouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR, true)

        assertTrue("read at the tap, so a refusal since composition counts", prompt.rationaleDue())
    }

    @Test
    fun `launching asks for every permission at once`() {
        val prompt = prompt()

        compose.runOnUiThread { prompt.launch() }

        val asked = shadowOf(compose.activity).lastRequestedPermission
        assertEquals(calendar, asked.requestedPermissions.toList())
    }
}
