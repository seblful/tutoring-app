package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A swipeable row filling a page that turns on a sideways drag — the shape of the homework tab. */
@RunWith(RobolectricTestRunner::class)
class SwipeDirectionTest {

    @get:Rule
    val compose = createComposeRule()

    private var confirmed = 0
    private var side by mutableStateOf(CURRENT)

    private fun rowOnPage(toward: SwipeToward, startOn: String) {
        side = startOn
        compose.setContent {
            KlasTheme {
                Box(Modifier.width(360.dp).swipeBetweenSegments(listOf(CURRENT, DONE), side) { side = it }) {
                    SwipeToConfirmBox(
                        onConfirm = { confirmed++ },
                        reveal = {},
                        toward = toward,
                        modifier = Modifier.testTag(ROW).fillMaxWidth().height(72.dp),
                    ) { Text("Законы Ньютона: задачи 5.12–5.20") }
                }
            }
        }
    }

    private fun swipe(right: Boolean) {
        compose.onNodeWithTag(ROW).performTouchInput { if (right) swipeRight() else swipeLeft() }
        compose.waitForIdle()
    }

    @Test
    fun `left to right over a done row turns back to current`() {
        rowOnPage(SwipeToward.Start, startOn = DONE)

        swipe(right = true)

        assertEquals(CURRENT, side)
        assertEquals("the row did not act", 0, confirmed)
    }

    @Test
    fun `right to left over a current row turns to done`() {
        rowOnPage(SwipeToward.End, startOn = CURRENT)

        swipe(right = false)

        assertEquals(DONE, side)
        assertEquals("the row did not act", 0, confirmed)
    }

    @Test
    fun `a drag the row's own way is the row's, not the page's`() {
        rowOnPage(SwipeToward.End, startOn = DONE)

        swipe(right = true)

        assertEquals(1, confirmed)
        assertEquals("the page stayed", DONE, side)
    }

    @Test
    fun `a row slid toward the start confirms past the mark`() {
        rowOnPage(SwipeToward.Start, startOn = CURRENT)

        swipe(right = false)

        assertEquals(1, confirmed)
        assertEquals("the page stayed", CURRENT, side)
    }

    private companion object {
        const val ROW = "row"
        const val CURRENT = "current"
        const val DONE = "done"
    }
}
