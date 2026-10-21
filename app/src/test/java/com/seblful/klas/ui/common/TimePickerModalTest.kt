package com.seblful.klas.ui.common

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class TimePickerModalTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private fun show(format: String, onConfirm: (LocalTime) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, format)
        compose.setContent { KlasTheme { TimePickerModal(LocalTime(21, 0), onConfirm, {}) } }
    }

    @Test
    fun `a phone using twelve hours offers AM and PM and preserves the evening hour`() {
        var saved: LocalTime? = null
        show("12") { saved = it }

        compose.onNodeWithText("PM").assertExists()
        compose.onNodeWithText("Done").performClick()

        assertEquals(LocalTime(21, 0), saved)
    }

    @Test
    fun `the time can be typed instead of dialled`() {
        var saved: LocalTime? = null
        show("24") { saved = it }

        compose.onNodeWithContentDescription("Switch to text input", substring = true).performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithContentDescription("Switch to clock", substring = true).assertExists()
        compose.onNodeWithText("Done").performClick()

        assertEquals(LocalTime(21, 0), saved)
    }

    @Test
    fun `a phone using twenty four hours offers no AM or PM`() {
        var saved: LocalTime? = null
        show("24") { saved = it }

        compose.onNodeWithText("PM").assertDoesNotExist()
        compose.onNodeWithText("AM").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()

        assertEquals(LocalTime(21, 0), saved)
    }
}
