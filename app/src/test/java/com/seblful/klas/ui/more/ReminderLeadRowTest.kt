package com.seblful.klas.ui.more

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
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

@RunWith(RobolectricTestRunner::class)
class ReminderLeadRowTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private var selected by mutableStateOf(60)

    private fun open(enabled: Boolean = true) {
        compose.setContent {
            KlasTheme {
                ReminderLeadRow(minutes = selected, enabled = enabled, onSelect = { selected = it })
            }
        }
    }

    private fun custom() {
        compose.onNodeWithText("Remind me").performClick()
        compose.onNodeWithText("Custom…").performClick()
    }

    @Test
    fun `a day before can be selected and changed to a custom number of days`() {
        open()
        compose.onNodeWithText("Remind me").performClick()
        compose.onNodeWithText("1 day before").performClick()
        assertEquals(1440, selected)
        compose.onNodeWithText("1 day before").assertExists()

        custom()
        compose.onNode(hasSetTextAction()).performTextReplacement("3")
        compose.onNodeWithText("Done").performClick()
        assertEquals(4320, selected)
        compose.onNodeWithText("3 days before").assertExists()

        custom()
        compose.onNode(hasSetTextAction()).performTextReplacement("2")
        compose.onNodeWithText("Done").performClick()
        assertEquals(2880, selected)
    }

    @Test
    fun `custom units convert to minutes and invalid amounts cannot be saved`() {
        open()
        custom()
        compose.onNodeWithText("Hours").performClick()
        compose.onNodeWithText("Days").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("2147483647")
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Done").assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextReplacement("2")
        compose.onNodeWithText("Days").performClick()
        compose.onNodeWithText("Hours").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(120, selected)
    }

    @Test
    fun `an existing custom minute value survives opening and cancelling the picker`() {
        selected = 90
        open()
        custom()
        compose.onNodeWithText("Done").performClick()
        assertEquals(90, selected)

        custom()
        compose.onNode(hasSetTextAction()).performTextReplacement("20")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(90, selected)

        custom()
        compose.onNode(hasSetTextAction()).performTextReplacement("20")
        compose.onNodeWithText("Done").performClick()
        assertEquals(20, selected)
        compose.onNodeWithText("20 minutes before").assertExists()
    }

    @Test
    fun `turning reminders off disables the timing row`() {
        open(enabled = false)
        compose.onNodeWithText("Remind me").assertIsNotEnabled()
        compose.onNodeWithText("Remind me").performClick()
        compose.onNodeWithText("Custom…").assertDoesNotExist()
        assertEquals(60, selected)
    }
}
