package com.seblful.klas.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.testutil.AppLocaleRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.util.Locale

/**
 * A lesson's time follows the **phone's** clock, not the app's language — in every window.
 *
 * With the phone's 12/24-hour switch on "automatic", Android answers from a context's locale. The app's
 * screens draw under a context localized to the app's language (`ProvideLocalizedResources`), but a bottom
 * sheet or a dialog is its own window with the phone's context — so a Russian app on an English phone printed
 * a lesson at 16:30 in the schedule and at 4:30 PM in that lesson's sheet. Both are now the phone's answer,
 * as the time picker and the reminders already were.
 */
@RunWith(RobolectricTestRunner::class)
class TimeTextTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val lesson = LocalTime.of(16, 30)

    @Test
    @Config(qualifiers = "en-rUS")
    fun `an English phone keeps its 12-hour clock under a Russian app`() {
        compose.setContent {
            ProvideLocalizedResources(Locale.forLanguageTag("ru")) { Text(lesson.timeText()) }
        }
        compose.onNodeWithText("4:30", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `a Russian phone keeps its 24-hour clock under an English app`() {
        compose.setContent {
            ProvideLocalizedResources(Locale.ENGLISH) { Text(lesson.timeText()) }
        }
        compose.onNodeWithText("16:30").assertExists()
    }
}
