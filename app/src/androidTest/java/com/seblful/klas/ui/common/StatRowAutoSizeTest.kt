package com.seblful.klas.ui.common

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

class StatRowAutoSizeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stats = listOf(
        Stat(label = "Занятий", value = "128", hint = "впереди 3"),
        Stat(label = "Часов", value = "192,5"),
        Stat(label = "Заработано", value = "1000000,00 BYN"),
    )

    @Test
    fun everyTileFitsAtEveryPhoneWidth() {
        val width = mutableStateOf(Widths.first())
        composeRule.setStatContent(width)

        val complaints = mutableListOf<String>()
        Widths.forEach { candidate ->
            composeRule.runOnIdle { width.value = candidate }
            composeRule.waitForIdle()
            stats.flatMap { listOfNotNull(it.value, it.label, it.hint) }.forEach { text ->
                val layout = composeRule.textLayoutOf(text)
                if (layout.lineCount != 1) {
                    complaints += "«$text» takes ${layout.lineCount} lines at $candidate"
                }
                if (layout.isLineEllipsized(0)) complaints += "«$text» is cut short at $candidate"
            }
        }
        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    @Test
    fun aValueWithRoomToSpareIsNeverEnlarged() {
        val width = mutableStateOf(NarrowPhone)
        composeRule.setStatContent(width)
        val narrow = composeRule.textLayoutOf(SHORT_VALUE).size.width

        composeRule.runOnIdle { width.value = LargePhone }
        composeRule.waitForIdle()
        val large = composeRule.textLayoutOf(SHORT_VALUE).size.width

        assertEquals(
            "«$SHORT_VALUE» fits either way, so it must be drawn at the same size either way",
            large,
            narrow,
        )
    }

    @Test
    fun theWidestAmountShrinksWhereTheTileIsTighter() {
        val width = mutableStateOf(NarrowPhone)
        composeRule.setStatContent(width)
        val narrow = composeRule.textLayoutOf(WIDEST_VALUE).size.width

        composeRule.runOnIdle { width.value = LargePhone }
        composeRule.waitForIdle()
        val large = composeRule.textLayoutOf(WIDEST_VALUE).size.width

        assertTrue(
            "«$WIDEST_VALUE» is ${narrow}px on a $NarrowPhone screen and ${large}px on a " +
                "$LargePhone one — it is not shrinking to fit the narrow tile",
            narrow < large,
        )
    }

    private fun ComposeContentTestRule.setStatContent(width: MutableState<Dp>) {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1f),
            ) {
                KlasTheme {
                    Box(Modifier.width(width.value)) {
                        StatRow(stats)
                    }
                }
            }
        }
        waitForIdle()
    }

    private companion object {
        /** Narrowest phone width still supported, the common one, and a large one. */
        val NarrowPhone = 320.dp
        val LargePhone = 411.dp
        val Widths = listOf(NarrowPhone, 360.dp, LargePhone)

        const val SHORT_VALUE = "128"
        const val WIDEST_VALUE = "1000000,00 BYN"
    }
}
