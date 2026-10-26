package com.seblful.klas.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w360dp-h740dp")
class DockedTabBarTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val selected = mutableStateOf<TopDestination?>(TopDestination.TODAY)
    private var tapped: TopDestination? = null
    private var clearance: Dp = 0.dp

    @Test
    fun `every tab is a tab named by its label and only the marked one is selected`() {
        setBar(TopDestination.PEOPLE)

        TopDestination.entries.forEach { dest ->
            val tab = compose.onNode(tabOf(dest) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            if (dest == TopDestination.PEOPLE) tab.assertIsSelected() else tab.assertIsNotSelected()
        }
    }

    @Test
    fun `a tap hands its tab over`() {
        setBar(TopDestination.TODAY)

        compose.onNode(tabOf(TopDestination.REPORTS)).performClick()

        assertEquals(TopDestination.REPORTS, tapped)
    }

    @Test
    fun `the room it reports reaches from the window's foot to the top of the bar`() {
        setBar(TopDestination.TODAY)

        val barTop = compose.onNodeWithTag(BAR).getBoundsInRoot().top
        assertEquals((compose.onRoot().getBoundsInRoot().bottom - barTop).value, clearance.value, 0.5f)
    }

    @Test
    fun `only the marked tab shows its label, and the label moves with the mark`() {
        setBar(TopDestination.TODAY)
        assertShownLabels(TopDestination.TODAY)

        compose.runOnIdle { selected.value = TopDestination.REPORTS }
        compose.waitForIdle()

        assertShownLabels(TopDestination.REPORTS)
    }

    @Test
    fun `held, an unmarked tab names itself and the marked one, already named, does not`() {
        setBar(TopDestination.PEOPLE)

        assertEquals(1, namesShownWhenHeld(TopDestination.PEOPLE))
        assertEquals(2, namesShownWhenHeld(TopDestination.REPORTS))
    }

    @Test
    fun `held, the marked tab shades its whole pill evenly`() {
        setBar(TopDestination.HOMEWORK)
        val tab = compose.onNode(tabOf(TopDestination.HOMEWORK))
        val idle = pillShades(tab)

        compose.mainClock.autoAdvance = false
        // At the icon, where a ripple would start: it must not stay a disc there inside the wider pill.
        tab.performTouchInput { down(Offset(width * 0.15f, centerY)) }
        compose.mainClock.advanceTimeBy(PRESS_SETTLED_MILLIS)
        val held = pillShades(tab)

        assertEquals("the held pill shows ${held.toSet().size} shades", 1, held.toSet().size)
        assertNotEquals("held, the pill is shaded", idle.first(), held.first())
    }

    @Test
    @Config(qualifiers = "ru-rRU-w320dp-h640dp")
    fun `on the narrowest phone every Russian label is whole once its tab is marked`() {
        setBar(TopDestination.TODAY)

        val complaints = TopDestination.entries.mapNotNull { dest ->
            compose.runOnIdle { selected.value = dest }
            compose.waitForIdle()
            val label = context.getString(dest.labelRes)
            val layout = layoutOf(label)
            "«$label» is cut short".takeIf { layout.lineCount != 1 || layout.isLineEllipsized(0) }
        }
        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    private fun assertShownLabels(marked: TopDestination) {
        TopDestination.entries.forEach { dest ->
            val width = compose.onNodeWithText(context.getString(dest.labelRes), useUnmergedTree = true)
                .getBoundsInRoot().let { it.right - it.left }.value
            if (dest == marked) {
                assertTrue("${dest.name}'s label should show", width > 0f)
            } else {
                assertEquals("${dest.name}'s label should be folded away", 0f, width, 0.5f)
            }
        }
    }

    /** How many times [dest]'s name is on screen while its tab is held: its label, and any bubble. */
    private fun namesShownWhenHeld(dest: TopDestination): Int {
        compose.onNode(tabOf(dest)).performTouchInput { longClick() }
        compose.waitForIdle()
        return compose.onAllNodesWithText(context.getString(dest.labelRes), useUnmergedTree = true)
            .fetchSemanticsNodes().size
    }

    /** A row across [tab]'s pill above its icon and label, short of its round ends. */
    private fun pillShades(tab: SemanticsNodeInteraction): List<Color> {
        val image = tab.captureToImage().toPixelMap()
        val y = with(compose.density) { image.height / 2 - 18.dp.roundToPx() }
        return (image.width / 4..image.width * 3 / 4).map { image[it, y] }
    }

    private fun layoutOf(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun setBar(initial: TopDestination) {
        selected.value = initial
        compose.setContent {
            KlasTheme {
                Box(Modifier.fillMaxSize()) {
                    DockedTabBar(
                        tabs = TopDestination.entries,
                        selected = selected.value,
                        onSelect = { tapped = it },
                        onClearance = { clearance = it },
                        modifier = Modifier.align(Alignment.BottomCenter).testTag(BAR),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tabOf(dest: TopDestination) = hasText(context.getString(dest.labelRes)) and isSelectable()

    private companion object {
        const val BAR = "bar"

        const val PRESS_SETTLED_MILLIS = 300L
    }
}
