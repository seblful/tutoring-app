package com.seblful.klas.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every tab label has to read as one whole word on one line. Five tabs (i.e. «Группы» shown) is the
 * tight case: «Расписание» takes all but a few pixels of its share of a 360dp screen, and it used to
 * wrap — leaving its last letter alone on a second line.
 *
 * Font scale is pinned to 1 throughout: these are assertions about the *layout*, and the device's own
 * font setting would otherwise decide whether they hold.
 */
class BottomBarLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyLabelReadsAsOneWholeWord_withGroupsTabShown() {
        val tabs = TopDestination.visible(showGroupsTab = true)
        assertEquals("this test is about the five-tab bar", 5, tabs.size)
        val width = mutableStateOf(Widths.first())
        composeRule.setBarContent(width, tabs)

        // Collected rather than asserted one at a time: which widths a label misbehaves at is the
        // interesting part, and the first failure would otherwise hide the rest.
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

    /**
     * How that one line is won: the type shrinks, and only for the label that would not otherwise
     * fit. Autosize enlarges as readily as it shrinks, so a label with room to spare must come out
     * exactly as wide as it is at the bar's own label size — the same width at every screen size.
     */
    @Test
    fun theTightestSlotShrinksOnlyTheLabelThatNeedsIt() {
        val width = mutableStateOf(NarrowPhone)
        composeRule.setBarContent(width, TopDestination.visible(showGroupsTab = true))

        val narrow = composeRule.widthsOf(TopDestination.SCHEDULE, TopDestination.MORE)
        composeRule.runOnIdle { width.value = LargePhone }
        composeRule.waitForIdle()
        val large = composeRule.widthsOf(TopDestination.SCHEDULE, TopDestination.MORE)

        assertEquals(
            "«Ещё» fits either way, so it must be drawn at the same size either way",
            large[TopDestination.MORE],
            narrow[TopDestination.MORE],
        )
        assertTrue(
            "«Расписание» is ${narrow[TopDestination.SCHEDULE]}px on a $NarrowPhone screen and " +
                "${large[TopDestination.SCHEDULE]}px on a $LargePhone one — it is not shrinking to fit",
            narrow.getValue(TopDestination.SCHEDULE) < large.getValue(TopDestination.SCHEDULE),
        )
    }

    private fun ComposeContentTestRule.setBarContent(
        width: MutableState<Dp>,
        tabs: List<TopDestination>,
    ) {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1f),
            ) {
                KlasTheme {
                    Box(Modifier.width(width.value)) {
                        BottomBar(tabs = tabs, currentRoute = Routes.SCHEDULE, onSelect = {})
                    }
                }
            }
        }
        waitForIdle()
    }

    /** The width each label is actually drawn at, in pixels. */
    private fun ComposeContentTestRule.widthsOf(vararg tabs: TopDestination): Map<TopDestination, Int> =
        tabs.associateWith { layoutOf(context.getString(it.labelRes)).size.width }

    /** The layout the label was actually drawn with — the one autosize settled on. */
    private fun ComposeContentTestRule.layoutOf(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.first()
    }

    private companion object {
        /** Narrowest phone width still supported, the common one, and a large one. */
        val NarrowPhone = 320.dp
        val LargePhone = 411.dp
        val Widths = listOf(NarrowPhone, 360.dp, LargePhone)
    }
}
