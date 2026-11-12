package com.seblful.klas.ui.common

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import kotlin.test.Test
import kotlin.test.assertEquals

class PredictiveBackTest {

    private fun held(edge: Int, progress: Float) = NavigationEventTransitionState.InProgress(
        NavigationEvent(swipeEdge = edge, progress = progress),
        NavigationEventTransitionState.TRANSITIONING_BACK,
    )

    @Test
    fun `no gesture pulls nothing`() {
        assertEquals(0f, backPull(NavigationEventTransitionState.Idle), 0f)
    }

    @Test
    fun `a swipe from the left pulls right by its progress`() {
        assertEquals(0.4f, backPull(held(NavigationEvent.EDGE_LEFT, 0.4f)), 0f)
    }

    @Test
    fun `a swipe from the right pulls left by its progress`() {
        assertEquals(-0.4f, backPull(held(NavigationEvent.EDGE_RIGHT, 0.4f)), 0f)
    }
}
