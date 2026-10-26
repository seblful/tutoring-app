package com.seblful.klas.ui.common

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.testutil.AppLocaleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ClipboardCopyTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val host = SnackbarHostState()

    private fun copyOnTap() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val copy = rememberClipboardCopy(Messages(host, scope, "Undo"))
            Text("Copy", Modifier.clickable { copy.copy("Phone", "+375 29 111-22-33") })
        }
        compose.onNodeWithText("Copy").performClick()
        compose.waitForIdle()
    }

    private fun clip() = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(ClipboardManager::class.java).primaryClip

    @Test
    fun `a copy puts the text as typed on the clipboard`() {
        copyOnTap()

        val clip = clip()!!
        assertEquals("Phone", clip.description.label.toString())
        assertEquals("+375 29 111-22-33", clip.getItemAt(0).text.toString())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun `before Android 13 the screen says it was copied`() {
        copyOnTap()

        assertEquals("Copied", host.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun `from Android 13 the system says it, and the screen stays quiet`() {
        copyOnTap()

        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `only Android 13 and later confirm a copy themselves`() {
        assertFalse(confirmsItself(Build.VERSION_CODES.S_V2))
        assertTrue(confirmsItself(Build.VERSION_CODES.TIRAMISU))
    }
}
