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

/**
 * Every tab label has to read as one whole word on one line.
 *
 * The bar carries **five** tabs — the case this test was written for, back: «Группы» was the fifth once,
 * until students and groups became one «Люди», and «Задания» is the fifth now. With five, «Расписание»
 * takes all but a few pixels of its fifth of a 360dp screen and used to wrap, stranding its last letter
 * on a second line; this is what proves it no longer does, rather than assuming so.
 *
 * Font scale is pinned to 1 except where a test is about it: these are assertions about the *layout*, and the device's own
 * font setting would otherwise decide whether they hold.
 */
class BottomBarLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyLabelReadsAsOneWholeWord() {
        val tabs = TopDestination.entries
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
     * The same, at the font sizes people actually turn up to — the one place the scale is not pinned.
     *
     * «Расписа…» was the bar at 1.3× on a 6.1" phone: the shrink's floor was in sp, so the scale that
     * made the word too wide also raised the size it was allowed to shrink to. The floor is a physical
     * size now, and this holds it there on the common phone widths.
     */
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

    /**
     * The cap on the autosizing, which is the half of it that is easy to get wrong.
     *
     * `TextAutoSize` enlarges as readily as it shrinks, so without a maximum a short label on a wide
     * screen would be drawn *bigger* than the bar's own label style asks for — «Ещё» quietly larger
     * than «Отчёты» beside it. A label with room to spare must therefore come out at exactly the same
     * size however much room there is.
     *
     * Asserted on «Ещё», which is the shortest label and fits at every width by a wide margin, so the
     * test says something about the cap rather than about how many tabs the bar happens to have.
     */
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
        // The longest label may shrink on a narrow screen or may now fit outright — four tabs give it
        // a quarter of the width instead of a fifth. What it must never do is grow.
        assertTrue(
            "«Расписание» is ${narrow[TopDestination.SCHEDULE]}px on a $NarrowPhone screen and " +
                "${large[TopDestination.SCHEDULE]}px on a $LargePhone one — it grew past its cap",
            narrow.getValue(TopDestination.SCHEDULE) <= large.getValue(TopDestination.SCHEDULE),
        )
    }

    /**
     * The tabs in **bar** form, whatever the window would otherwise ask for.
     *
     * `NavigationSuiteScaffold` picks between a bar and a rail from the window's width class, and this
     * test is about the bar: it constrains a `Box` to a phone's width, which says nothing about the
     * window. Pinning [NavigationSuiteType.NavigationBar] is what keeps it measuring the case the labels
     * are tight in — a rail has width to spare, so a test that accidentally got one would pass whatever
     * the labels did.
     */
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
                            layoutType = NavigationSuiteType.NavigationBar,
                            navigationSuiteItems = {
                                klasTabs(tabs = tabs, selected = TopDestination.SCHEDULE, onSelect = {})
                            },
                            content = {},
                        )
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
