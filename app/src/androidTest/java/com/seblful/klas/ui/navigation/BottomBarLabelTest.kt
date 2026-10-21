package com.seblful.klas.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.testutil.textLayoutOf
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BottomBarLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyLabelReadsAsOneWholeWord() {
        val tabs = TopDestination.entries
        val width = mutableStateOf(Widths.first())
        composeRule.setBarContent(width, tabs)

        val complaints = mutableListOf<String>()
        Widths.forEach { candidate ->
            composeRule.runOnIdle { width.value = candidate }
            composeRule.waitForIdle()
            tabs.forEach { dest ->
                val label = context.getString(dest.labelRes)
                val layout = composeRule.layoutOf(label)
                if (layout.lineCount != 1) {
                    complaints += "«$label» takes ${layout.lineCount} lines at $candidate"
                }
                if (layout.isLineEllipsized(0)) complaints += "«$label» is cut short at $candidate"
            }
        }
        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    @Test
    fun aLargeFontStillShowsWholeWords() {
        val tabs = TopDestination.entries
        val complaints = mutableListOf<String>()
        val width = mutableStateOf(360.dp)
        val fontScale = mutableStateOf(LargeFontScales.first())
        composeRule.setBarContent(width, tabs, fontScale)
        LargeFontScales.forEach { scale ->
            listOf(360.dp, LargePhone).forEach { candidate ->
                composeRule.runOnIdle {
                    fontScale.value = scale
                    width.value = candidate
                }
                composeRule.waitForIdle()
                tabs.forEach { dest ->
                    val label = context.getString(dest.labelRes)
                    val layout = composeRule.layoutOf(label)
                    if (layout.lineCount != 1 || layout.isLineEllipsized(0)) {
                        complaints += "«$label» is not whole at $candidate, font scale $scale"
                    }
                }
            }
        }
        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    @Test
    fun aLabelWithRoomToSpareIsNeverEnlarged() {
        val width = mutableStateOf(NarrowPhone)
        composeRule.setBarContent(width, TopDestination.entries)

        val narrow = composeRule.widthsOf(TopDestination.MORE, TopDestination.SCHEDULE)
        composeRule.runOnIdle { width.value = LargePhone }
        composeRule.waitForIdle()
        val large = composeRule.widthsOf(TopDestination.MORE, TopDestination.SCHEDULE)

        assertEquals(
            "«Ещё» fits either way, so it must be drawn at the same size either way",
            large[TopDestination.MORE],
            narrow[TopDestination.MORE],
        )
        assertTrue(
            "«Расписание» is ${narrow[TopDestination.SCHEDULE]}px on a $NarrowPhone screen and " +
                "${large[TopDestination.SCHEDULE]}px on a $LargePhone one — it grew past its cap",
            narrow.getValue(TopDestination.SCHEDULE) <= large.getValue(TopDestination.SCHEDULE),
        )
    }

    private fun ComposeContentTestRule.setBarContent(
        width: MutableState<Dp>,
        tabs: List<TopDestination>,
        fontScale: MutableState<Float> = mutableStateOf(1f),
    ) {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale.value),
            ) {
                KlasTheme {
                    Box(Modifier.width(width.value)) {
                        NavigationSuiteScaffold(
                            navigationItems = {
                                KlasTabs(tabs = tabs, selected = TopDestination.SCHEDULE, onSelect = {})
                            },
                            navigationSuiteType = NavigationSuiteType.ShortNavigationBarCompact,
                            content = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeContentTestRule.widthsOf(vararg tabs: TopDestination): Map<TopDestination, Int> =
        tabs.associateWith { layoutOf(context.getString(it.labelRes)).size.width }

    private fun ComposeContentTestRule.layoutOf(label: String): TextLayoutResult =
        textLayoutOf(label)

    private companion object {
        /** Narrowest phone width still supported, the common one, and a large one. */
        val NarrowPhone = 320.dp
        val LargePhone = 411.dp
        val Widths = listOf(NarrowPhone, 360.dp, LargePhone)

        /**
         * The larger text steps people commonly use. 2× — the platform's extreme — is left out on purpose:
         * there the longest English labels are still cut at the 9 dp floor, and going lower would make
         * every label unreadable to keep two whole.
         */
        val LargeFontScales = listOf(1.3f, 1.5f)
    }
}
