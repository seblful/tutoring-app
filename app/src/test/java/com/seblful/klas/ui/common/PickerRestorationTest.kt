package com.seblful.klas.ui.common

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
class PickerRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @Test
    fun `a duration restores both typed fields and confirms their restored value`() {
        val restoration = StateRestorationTester(compose)
        var confirmed: Int? = null
        restoration.setContent {
            KlasTheme { DurationPickerModal(60, { confirmed = it }, {}) }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("2")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("15")

        restoration.emulateSavedInstanceStateRestore()

        compose.onAllNodes(hasSetTextAction())[0].assertTextContains("2")
        compose.onAllNodes(hasSetTextAction())[1].assertTextContains("15")
        compose.onNodeWithText("Done").assertIsEnabled().performClick()
        assertEquals(135, confirmed)
    }

    @Test
    fun `a repeat count restores typed input and confirms it instead of the initial count`() {
        val restoration = StateRestorationTester(compose)
        var confirmed: Int? = null
        restoration.setContent {
            KlasTheme { CountPickerModal("Repeat", "Lessons", 3, 52, { confirmed = it }, {}) }
        }
        compose.onNodeWithText("Lessons").performTextReplacement("7")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Lessons").assertTextContains("7")
        compose.onNodeWithText("Done").assertIsEnabled().performClick()
        assertEquals(7, confirmed)
    }
}
