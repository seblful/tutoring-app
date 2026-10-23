package com.seblful.klas.lock

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLockGateTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val prompts = mutableListOf<String>()

    private fun gate(locked: Boolean) = compose.setContent {
        KlasTheme { AppLockGate(locked, onUnlock = { prompts += it }) { Text("Ava Thompson owes 90 $") } }
    }

    @Test
    fun `locked, the content is out of reach and the prompt opens by itself`() {
        gate(locked = true)

        compose.onNodeWithText("Ava Thompson owes 90 $").assertDoesNotExist()
        // The app's own name, which a debug build spells «Klas-debug».
        compose.onNodeWithText("is locked", substring = true).assertExists()
        assertTrue(prompts.single().startsWith("Unlock "))

        compose.onNodeWithText("Unlock").performClick()
        assertEquals("a dismissed prompt can be asked for again", 2, prompts.size)
    }

    @Test
    fun `open, the content shows and nothing is asked`() {
        gate(locked = false)

        compose.onNodeWithText("Ava Thompson owes 90 $").assertExists()
        compose.onNodeWithText("is locked", substring = true).assertDoesNotExist()
        assertEquals(emptyList<String>(), prompts)
    }
}
