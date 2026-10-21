package com.seblful.klas.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Back between screens as the gesture draws it: the screen leaving shrinks, and comes back on a cancel. */
@RunWith(RobolectricTestRunner::class)
class PredictivePopTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val backStack = mutableStateListOf(BENEATH, LEAVING)

    @Test
    fun `a held back gesture shrinks the leaving screen over the one beneath`() {
        compose.setContent {
            NavDisplay(
                backStack = backStack,
                transitionSpec = { fadeThrough() },
                popTransitionSpec = { fadeThrough() },
                predictivePopTransitionSpec = { edge -> predictiveBackPop(edge, shiftInset = 0) },
                onBack = { backStack.removeLastOrNull() },
            ) { key -> NavEntry(key) { Box(Modifier.fillMaxSize().testTag(key)) } }
        }
        val resting = compose.onNodeWithTag(LEAVING).fetchSemanticsNode().boundsInRoot

        gesture { it.dispatchOnBackStarted(event(0f)) }
        gesture { it.dispatchOnBackProgressed(event(0.5f)) }

        val held = compose.onNodeWithTag(LEAVING).fetchSemanticsNode().boundsInRoot
        assertTrue("narrower while held: $resting → $held", held.width < resting.width)
        assertTrue("drifts away from the left edge: $held", held.left > resting.left)
        compose.onNodeWithTag(BENEATH).assertExists()

        gesture { it.dispatchOnBackCancelled() }
        assertEquals(resting, compose.onNodeWithTag(LEAVING).fetchSemanticsNode().boundsInRoot)
        assertEquals(listOf(BENEATH, LEAVING), backStack.toList())
    }

    private fun gesture(dispatch: (OnBackPressedDispatcher) -> Unit) {
        compose.runOnIdle { dispatch(compose.activity.onBackPressedDispatcher) }
        compose.waitForIdle()
    }

    private fun event(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private companion object {
        const val BENEATH = "beneath"
        const val LEAVING = "leaving"
    }
}
