package com.seblful.klas.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [predictiveBack] under a real gesture: drawn from it, without recomposing whoever applied it. */
@RunWith(RobolectricTestRunner::class)
class PredictiveBackGestureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var compositions = 0

    @Composable
    private fun Host() {
        val state = rememberNavigationEventState(NavigationEventInfo.None)
        NavigationBackHandler(state = state, onBackCompleted = {})
        SideEffect { compositions++ }
        Box(Modifier.size(200.dp).predictiveBack(state).testTag(TAG))
    }

    @Test
    fun `a held gesture shrinks the content without recomposing the scope that applied it`() {
        compose.setContent { Host() }
        val resting = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInRoot
        val before = compositions

        gesture { it.dispatchOnBackStarted(event(0f)) }
        listOf(0.2f, 0.5f, 0.8f).forEach { progress -> gesture { it.dispatchOnBackProgressed(event(progress)) } }

        val held = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("narrower while held: $resting → $held", held.width < resting.width)
        assertEquals("recomposed while the gesture moved", before, compositions)

        gesture { it.dispatchOnBackCancelled() }
        assertEquals(resting, compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInRoot)
    }

    private fun gesture(dispatch: (OnBackPressedDispatcher) -> Unit) {
        compose.runOnIdle { dispatch(compose.activity.onBackPressedDispatcher) }
        compose.waitForIdle()
    }

    private fun event(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private companion object {
        const val TAG = "pulled"
    }
}
