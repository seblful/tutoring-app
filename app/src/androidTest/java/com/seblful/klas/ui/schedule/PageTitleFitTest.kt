package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.testutil.textLayoutOf
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PageTitleFitTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theLongestDayFitsAtEveryPhoneWidth() {
        val complaints = mutableListOf<String>()
        val shown = mutableStateOf(LongestTitles.first())
        val width = mutableStateOf(Widths.first())
        val fontScale = mutableStateOf(FontScales.first())
        composeRule.setNavigator(shown, width, fontScale)
        FontScales.forEach { scale ->
            LongestTitles.forEach { title ->
                Widths.forEach { candidate ->
                    composeRule.runOnIdle {
                        fontScale.value = scale
                        shown.value = title
                        width.value = candidate
                    }
                    composeRule.waitForIdle()
                    val layout = composeRule.textLayoutOf(title)
                    val where = "$candidate, font scale $scale"
                    if (layout.lineCount != 1) complaints += "«$title» takes ${layout.lineCount} lines at $where"
                    if (layout.isLineEllipsized(0)) complaints += "«$title» is cut short at $where"
                }
            }
        }
        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    @Test
    fun aShortTitleIsNeverEnlarged() {
        val width = mutableStateOf(NarrowPhone)
        composeRule.setNavigator(mutableStateOf(SHORT_TITLE), width)
        val narrow = composeRule.textLayoutOf(SHORT_TITLE).size.width

        composeRule.runOnIdle { width.value = LargePhone }
        composeRule.waitForIdle()

        assertEquals(narrow, composeRule.textLayoutOf(SHORT_TITLE).size.width)
    }

    private fun ComposeContentTestRule.setNavigator(
        title: MutableState<String>,
        width: MutableState<Dp>,
        fontScale: MutableState<Float> = mutableStateOf(1f),
    ) {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale.value)) {
                KlasTheme {
                    Box(Modifier.width(width.value)) {
                        PageNavigator(
                            title = title.value,
                            previousDescription = "назад",
                            nextDescription = "вперёд",
                            onPrevious = {},
                            onNext = {},
                            onToday = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    private companion object {
        val NarrowPhone = 320.dp
        val LargePhone = 411.dp
        val Widths = listOf(NarrowPhone, 360.dp, LargePhone)

        /** The default, and the larger-text step many people use — where the floor's unit showed. */
        val FontScales = listOf(1f, 1.3f)

        /** The longest weekday with the longest genitive month, in both languages the app speaks. */
        val LongestTitles = listOf("Понедельник, 30 сентября", "Wednesday, 30 September")

        const val SHORT_TITLE = "Июль 2026"
    }
}
