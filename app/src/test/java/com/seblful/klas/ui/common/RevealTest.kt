package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RevealTest {

    @get:Rule
    val compose = createComposeRule()

    private var visible by mutableStateOf(false)

    private fun show() = compose.setContent {
        Column(Modifier.testTag(TAG)) { Reveal(visible) { Box(Modifier.size(100.dp)) } }
    }

    private fun height() = compose.onNodeWithTag(TAG).fetchSemanticsNode().size.height

    private val full get() = with(compose.density) { 100.dp.roundToPx() }

    @Test
    fun `it grows into place and folds away again, pushing what is under it gradually`() {
        show()
        assertEquals(0, height())

        compose.mainClock.autoAdvance = false
        visible = true
        // With the clock held, a write from the test thread reaches the recomposer only when sent.
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(64)
        val growing = height()
        assertTrue("part of the way: $growing of $full", growing in 1 until full)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(full, height())

        visible = false
        compose.waitForIdle()
        assertEquals(0, height())
    }

    @Test
    fun `shown from the start, it does not grow`() {
        visible = true
        compose.mainClock.autoAdvance = false
        show()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(full, height())
    }

    private companion object {
        const val TAG = "column"
    }
}
