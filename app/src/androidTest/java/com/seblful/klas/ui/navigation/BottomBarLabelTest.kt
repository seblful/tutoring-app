package com.seblful.klas.ui.navigation

import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
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
    fun aLargeFontNamesEveryTabAndKeepsTheSelectedLabelWhole() {
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
                    composeRule.onNode(hasText(label) and isSelectable()).assertIsDisplayed()
                }
                val label = context.getString(TopDestination.SCHEDULE.labelRes)
                val layout = composeRule.layoutOf(label)
                if (layout.lineCount != 1 ||
                    layout.isLineEllipsized(0)
                ) complaints += "«$label» is not whole at $candidate, font scale $scale"
            }
        }
        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    /**
     * Read from the window's accessibility tree, what TalkBack and UiAutomator see: Compose's test tree still
     * holds a folded label's text, but a transparent node is left out of the real one.
     */
    @Test
    fun everyTabIsNamedToAccessibilityServices() {
        composeRule.setBarContent(mutableStateOf(360.dp), TopDestination.entries)

        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Connecting UiAutomation is what turns accessibility on; Compose fills the tree a moment later.
        var spoken = emptyList<String>()
        composeRule.waitUntil(A11Y_TREE_TIMEOUT_MS) {
            spoken = spokenNames(automation.rootInActiveWindow)
            spoken.isNotEmpty()
        }
        val unnamed = TopDestination.entries.map { context.getString(it.labelRes) }.filterNot { it in spoken }
        assertTrue("Tabs TalkBack cannot name: $unnamed; it reads $spoken", unnamed.isEmpty())
    }

    @Test
    fun aLabelWithRoomToSpareIsNeverEnlarged() {
        val width = mutableStateOf(NarrowPhone)
        composeRule.setBarContent(width, TopDestination.entries)

        val narrow = composeRule.widthsOf(TopDestination.PEOPLE, TopDestination.SCHEDULE)
        composeRule.runOnIdle { width.value = LargePhone }
        composeRule.waitForIdle()
        val large = composeRule.widthsOf(TopDestination.PEOPLE, TopDestination.SCHEDULE)

        assertEquals(
            "«Люди» fits either way, so it must be drawn at the same size either way",
            large[TopDestination.PEOPLE],
            narrow[TopDestination.PEOPLE],
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
                        DockedTabBar(tabs = tabs, selected = TopDestination.SCHEDULE, onSelect = {}, onClearance = {})
                    }
                }
            }
        }
        waitForIdle()
    }

    /** Each node's name as TalkBack speaks it: its description when it has one, else its text. */
    private fun spokenNames(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val own = (node.contentDescription?.takeIf { it.isNotEmpty() } ?: node.text)?.toString()
        return listOfNotNull(own) + (0 until node.childCount).flatMap { spokenNames(node.getChild(it)) }
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

        val LargeFontScales = listOf(1.3f, 1.5f, 2f)

        const val A11Y_TREE_TIMEOUT_MS = 5_000L
    }
}
