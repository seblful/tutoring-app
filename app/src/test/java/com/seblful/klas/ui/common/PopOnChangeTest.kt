package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PopOnChangeTest {

    @get:Rule
    val compose = createComposeRule()

    // Tagged inside the popping box: a node's own bounds leave out the layer it is placed in.
    @Composable
    private fun Popping(value: Int, tag: String) {
        Box(Modifier.size(100.dp).popsOnChange(value)) { Box(Modifier.fillMaxSize().testTag(tag)) }
    }

    /** [tag]'s width over the next third of a second, frame by frame — a pop is over in fewer. */
    private fun framesOf(tag: String): List<Float> = (1..20).map {
        compose.mainClock.advanceTimeByFrame()
        width(tag)
    }

    private fun width(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width

    @Test
    fun `a change pops and settles, and the first value does not`() {
        var value by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent { Popping(value, TAG) }
        compose.mainClock.advanceTimeByFrame()
        val rest = width(TAG)
        assertEquals("popped on arriving", listOf(rest), framesOf(TAG).distinct())

        value = 1
        val seen = framesOf(TAG)
        assertTrue("no pop on a change: rest $rest, then $seen", seen.any { it > rest })

        compose.mainClock.advanceTimeBy(SETTLE)
        assertEquals(rest, width(TAG))
    }

    @Test
    fun `a card scrolled into a reused slot is not a change`() {
        lateinit var scope: CoroutineScope
        lateinit var state: LazyListState
        compose.setContent {
            scope = rememberCoroutineScope()
            state = rememberLazyListState()
            LazyColumn(Modifier.height(300.dp), state = state) {
                // Each card its own value, so a slot handed to the next card hands it a different one.
                items(200, key = { it }, contentType = { "card" }) { index ->
                    Popping(index, "card$index")
                }
            }
        }
        val rest = width("card0")

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { scope.launch { state.scrollToItem(100) } }
        compose.mainClock.advanceTimeByFrame()

        assertEquals("a recycled card popped", listOf(rest), framesOf("card100").distinct())
    }

    private companion object {
        const val TAG = "popped"
        const val SETTLE = 3_000L
    }
}
