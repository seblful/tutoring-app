package com.seblful.klas.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.Privacy
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivacySettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private var settings by mutableStateOf(AppSettings())

    private fun page(lockAvailable: Boolean = true) = compose.setContent {
        KlasTheme { PrivacySettingsContent(settings, { settings = it(settings) }, lockAvailable) }
    }

    @Test
    fun `everything starts private and each choice is the tutor's to make`() {
        page()

        compose.onNodeWithText("Lesson details in notifications").performClick()
        compose.onNodeWithText("Lessons in the lock-screen widget").performClick()

        assertTrue(settings.privacy.lockScreenDetails)
        assertTrue(settings.privacy.lockScreenWidgets)
        assertFalse(settings.privacy.appLock)
    }

    @Test
    fun `the relock delay is offered once the app lock is on`() {
        page()
        compose.onNodeWithText("Lock again after").assertDoesNotExist()

        compose.onNodeWithText("App lock").performClick()

        assertTrue(settings.privacy.appLock)
        compose.onNodeWithText("Lock again after").assertExists()
        compose.onNodeWithText("The app lock hides the preview already").assertExists()
    }

    @Test
    fun `a phone with no screen lock is not offered a lock it could not open`() {
        page(lockAvailable = false)

        compose.onNodeWithText("Set a screen lock on this phone first").assertExists()
        compose.onNodeWithText("App lock").assertIsNotEnabled()
        assertEquals(Privacy(), settings.privacy)
    }
}
