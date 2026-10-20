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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TabReselectFadeTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var navController: NavHostController

    @Before
    fun showTabs() {
        rule.setContent {
            navController = rememberNavController()
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                NavHost(navController, startDestination = Routes.Schedule) {
                    screen<Routes.Schedule> { Box(Modifier.fillMaxSize().background(Color.White)) }
                    screen<Routes.Homework> { Box(Modifier.fillMaxSize().background(Color.Black)) }
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

    private fun fadeToHomework(secondTapAfterFrames: Int?): List<Float> {
        rule.mainClock.autoAdvance = true
        rule.runOnUiThread { navController.navigateToTab(TopDestination.SCHEDULE) }
        rule.waitForIdle()

        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { navController.navigateToTab(TopDestination.HOMEWORK) }
        return List(Frames) { frame ->
            if (frame == secondTapAfterFrames) {
                rule.runOnUiThread { navController.navigateToTab(TopDestination.HOMEWORK) }
            }
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
