package com.seblful.klas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RootSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    private var route by mutableStateOf(RootRoute.LOADING)

    private fun show(from: RootRoute) {
        route = from
        compose.setContent { RootSwitch(route) { shown -> Box(Modifier.fillMaxSize().testTag(shown.name)) } }
        compose.waitForIdle()
    }

    private fun width(of: RootRoute) = compose.onNodeWithTag(of.name).fetchSemanticsNode().boundsInRoot.width

    @Test
    fun `out of loading it cuts, the splash having covered the change`() {
        show(RootRoute.LOADING)
        val window = width(RootRoute.LOADING)

        compose.mainClock.autoAdvance = false
        route = RootRoute.MAIN
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(64)

        // A fade through would still be growing the app in from 92% here.
        compose.onNodeWithTag(RootRoute.LOADING.name).assertDoesNotExist()
        assertEquals(window, width(RootRoute.MAIN))
    }

    @Test
    fun `setup gives way to the app by fade through`() {
        show(RootRoute.ONBOARDING)
        val window = width(RootRoute.ONBOARDING)

        compose.mainClock.autoAdvance = false
        route = RootRoute.MAIN
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(64)

        // Setup is still leaving, and the app waits beneath at the scale it will grow from.
        compose.onNodeWithTag(RootRoute.ONBOARDING.name).assertExists()
        assertTrue("arriving smaller: ${width(RootRoute.MAIN)} of $window", width(RootRoute.MAIN) < window)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag(RootRoute.ONBOARDING.name).assertDoesNotExist()
        assertEquals(window, width(RootRoute.MAIN))
    }
}
