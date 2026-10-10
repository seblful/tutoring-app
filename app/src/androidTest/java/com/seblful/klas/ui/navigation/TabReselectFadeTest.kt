package com.seblful.klas.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * **Tapping the tab that is already arriving does not disturb its fade.**
 *
 * The bottom bar's glitch: on a cold or idle app the first frames of a tab's fade are the slow ones, and a
 * second tap on the same tab lands inside them. `navigateToTab` used to run for it too — pop back to the
 * start destination, restore the same entry — which re-targets the running transition, so the screen
 * lurched forward (one frame went from 0.92 to 0.17 of the way) instead of finishing the fade it was in.
 *
 * Asserted on **pixels**, one frame at a time on a paused clock, as `ItemMotionTest` does, because what is
 * wrong is the *shape* of the fade: the back stack, the semantics tree and the settled screen are all
 * right afterwards, so a node assertion passes either way. Each screen is one solid colour; the second tap
 * lands after one frame and after two (a tap after four frames was always harmless, and one before any
 * frame has drawn is too), and the fade it draws must be the one a single tap draws.
 */
@HiltAndroidTest
class TabReselectFadeTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var navController: NavHostController

    @Before
    fun showTabs() {
        rule.setContent {
            navController = rememberNavController()
            // Red behind both screens: a frame where neither covers it would show.
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                NavHost(navController, startDestination = Routes.SCHEDULE) {
                    screen(Routes.SCHEDULE) { Box(Modifier.fillMaxSize().background(Color.White)) }
                    screen(Routes.HOMEWORK) { Box(Modifier.fillMaxSize().background(Color.Black)) }
                }
            }
        }
    }

    @Test
    fun aSecondTapOneFrameIntoTheFadeDrawsTheFadeOfASingleTap() {
        val single = fadeToHomework(secondTapAfterFrames = null)

        assertFadeIs(single, fadeToHomework(secondTapAfterFrames = 1))
    }

    @Test
    fun aSecondTapTwoFramesIntoTheFadeDrawsTheFadeOfASingleTap() {
        val single = fadeToHomework(secondTapAfterFrames = null)

        assertFadeIs(single, fadeToHomework(secondTapAfterFrames = 2))
    }

    /** The centre pixel's luminance for each of the [Frames] frames after the tab is tapped on the schedule. */
    private fun fadeToHomework(secondTapAfterFrames: Int?): List<Float> {
        // Back to the schedule on a running clock, so every fade starts from the same place.
        rule.mainClock.autoAdvance = true
        rule.runOnUiThread { navController.navigateToTab(Routes.SCHEDULE) }
        rule.waitForIdle()

        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { navController.navigateToTab(Routes.HOMEWORK) }
        return List(Frames) { frame ->
            if (frame == secondTapAfterFrames) rule.runOnUiThread { navController.navigateToTab(Routes.HOMEWORK) }
            rule.mainClock.advanceTimeByFrame()
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2].luminance()
        }
    }

    private fun assertFadeIs(expected: List<Float>, actual: List<Float>) {
        expected.indices.forEach { frame ->
            assertEquals("frame $frame of $actual", expected[frame], actual[frame], Tolerance)
        }
    }

    private companion object {
        /** The fade lasts 180 ms, about eleven frames; the rest is it settled. */
        const val Frames = 16
        const val Tolerance = 0.02f
    }
}
