package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * iOS's half of `InkCentredTextTest` in `:app`: measured on the pixels Skia draws, at an iPhone's 3×, in the
 * Golos the theme loads from Compose resources.
 */
@OptIn(ExperimentalTestApi::class)
class InkCentredTextIosTest {

    @Test
    fun `a day number's digits sit at the middle of its disc`() = runComposeUiTest {
        offsetsOf(centred = true).forEach { (number, offset) ->
            val (dx, dy) = offset
            assertTrue(abs(dx) <= ONE_PIXEL, "«$number» is ${dx}px right of the middle")
            assertTrue(abs(dy) <= ONE_PIXEL, "«$number» is ${dy}px below the middle")
        }
    }

    /** Without this, the test above would pass on a centring that measured nothing. */
    @Test
    fun `a centred line box alone holds them low`() = runComposeUiTest {
        val dy = offsetsOf(centred = false).values.map { it.second }.average()
        assertTrue(dy > HALF_PIXEL, "on average only ${dy}px below the middle")
    }

    private fun ComposeUiTest.offsetsOf(centred: Boolean): Map<String, Pair<Float, Float>> {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(IPHONE_DENSITY)) {
                KlasTheme {
                    val style = MaterialTheme.typography.bodyMedium
                    Column {
                        Numbers.chunked(DAYS_IN_WEEK).forEach { week ->
                            Row {
                                week.forEach { number ->
                                    Box(
                                        Modifier.size(28.dp).background(Color.White).testTag(number),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (centred) {
                                            InkCentredText(number, style, color = { Color.Black })
                                        } else {
                                            BasicText(number, style = style.copy(color = Color.Black))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return Numbers.associateWith { inkOffCentre(it) }
    }

    /** How far right of and below its disc's middle the number's ink is centred, in pixels. */
    private fun ComposeUiTest.inkOffCentre(number: String): Pair<Float, Float> {
        val pixels = onNodeWithTag(number).captureToImage().toPixelMap()
        var left = pixels.width
        var right = -1
        var top = pixels.height
        var bottom = -1
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (pixels[x, y].red < INK) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        assertTrue(right >= 0, "«$number» drew no ink")
        val inkX = (left + right + 1) / 2f
        val inkY = (top + bottom + 1) / 2f
        return (inkX - pixels.width / 2f) to (inkY - pixels.height / 2f)
    }

    private companion object {
        val Numbers = (1..31).map(Int::toString)
        const val ONE_PIXEL = 1f
        const val HALF_PIXEL = 0.5f
        const val DAYS_IN_WEEK = 7
        const val IPHONE_DENSITY = 3f

        /** Darker than this is a digit's ink, antialiased edge included. */
        const val INK = 0.75f
    }
}
