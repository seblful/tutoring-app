package com.seblful.klas.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class KlasScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @Test
    fun `a pushed screen exposes its title and delegates back to the caller`() {
        var backs = 0
        compose.setContent {
            KlasTheme { KlasScreen("Student", onBack = { backs++ }) { _, _ -> Text("Details") } }
        }

        compose.onNodeWithText("Student").assertIsDisplayed()
        compose.onNodeWithText("Details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `a top-level screen offers no back action`() {
        compose.setContent { KlasTheme { KlasScreen("Schedule") { _, _ -> } } }

        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}
