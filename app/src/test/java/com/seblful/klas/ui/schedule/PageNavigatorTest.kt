package com.seblful.klas.ui.schedule

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class PageNavigatorTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val selected = LocalDate(2026, 6, 1)

    @Test
    fun `the page title is a heading that opens a date picker on the selected day`() {
        val picked = mutableListOf<LocalDate>()
        compose.setContent {
            KlasTheme {
                PageNavigator(
                    title = "June 2026",
                    previousDescription = "Previous",
                    nextDescription = "Next",
                    onPrevious = {},
                    onNext = {},
                    onToday = {},
                    selected = selected,
                    onPick = { picked += it },
                )
            }
        }

        compose.onNodeWithText("June 2026")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .performClick()
        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf(selected), picked)
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `arrow keys on the focused title page the dates`() {
        val steps = mutableListOf<String>()
        compose.setContent {
            KlasTheme {
                PageNavigator(
                    title = "June 2026",
                    previousDescription = "Previous",
                    nextDescription = "Next",
                    onPrevious = { steps += "previous" },
                    onNext = { steps += "next" },
                    onToday = {},
                    selected = selected,
                    onPick = {},
                )
            }
        }

        val title = compose.onNodeWithText("June 2026")
        title.performKeyInput { pressKey(Key.Tab) }
        title.requestFocus()
        title.performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionLeft)
        }

        assertEquals(listOf("next", "previous"), steps)
    }

    @Test
    fun `cancelling the picker picks nothing`() {
        val picked = mutableListOf<LocalDate>()
        compose.setContent {
            KlasTheme {
                PageNavigator(
                    title = "June 2026",
                    previousDescription = "Previous",
                    nextDescription = "Next",
                    onPrevious = {},
                    onNext = {},
                    onToday = {},
                    selected = selected,
                    onPick = { picked += it },
                )
            }
        }

        compose.onNodeWithText("June 2026").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList<LocalDate>(), picked)
    }
}
