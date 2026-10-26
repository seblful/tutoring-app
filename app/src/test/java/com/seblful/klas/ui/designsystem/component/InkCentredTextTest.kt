package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Measured on the drawn pixels, at a phone's density: what the eye judges, not what the font claims. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class InkCentredTextTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a day number's digits sit at the middle of its disc`() {
        val offsets = offsetsOf(centred = true)
        offsets.forEach { (number, offset) ->
            val (dx, dy) = offset
            assertTrue("«$number» is ${dx}px right of the middle", abs(dx) <= ONE_PIXEL)
            assertTrue("«$number» is ${dy}px below the middle", abs(dy) <= ONE_PIXEL)
        }
    }

    @Test
    fun `a centred line box alone holds them low and left`() {
        val offsets = offsetsOf(centred = false)
        val dx = offsets.values.map { it.first }.average()
        val dy = offsets.values.map { it.second }.average()
        assertTrue("on average only ${dx}px right of the middle", dx < -HALF_PIXEL)
        assertTrue("on average only ${dy}px below the middle", dy > ONE_PIXEL)
    }

    /** How far right of and below its disc's middle each number's ink is centred, in pixels. */
    private fun offsetsOf(centred: Boolean): Map<String, Pair<Float, Float>> {
        compose.setContent {
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
        return Numbers.associateWith { inkOffCentre(it) }
    }

    private fun inkOffCentre(number: String): Pair<Float, Float> {
        val pixels = compose.onNodeWithTag(number).captureToImage().toPixelMap()
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
        val inkX = (left + right + 1) / 2f
        val inkY = (top + bottom + 1) / 2f
        return (inkX - pixels.width / 2f) to (inkY - pixels.height / 2f)
    }

    private companion object {
        val Numbers = (1..31).map(Int::toString)
        const val ONE_PIXEL = 1f
        const val HALF_PIXEL = 0.5f
        const val DAYS_IN_WEEK = 7

        /** Darker than this is a digit's ink, antialiased edge included. */
        const val INK = 0.75f
    }
}
