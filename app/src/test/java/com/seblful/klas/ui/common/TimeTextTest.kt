package com.seblful.klas.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.testutil.AppLocaleRule
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TimeTextTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val lesson = LocalTime(16, 30)

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
