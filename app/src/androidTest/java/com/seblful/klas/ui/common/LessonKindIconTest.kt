package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.ui.common.state.map
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class LessonKindIconTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Ink(val width: Int, val height: Int, val color: Color)

    private var primary: Color = Color.Unspecified

    @Test
    fun bothKindsAreDrawnToTheSameHeightInTheSameColour() {
        composeRule.setContent {
            KlasTheme {
                primary = MaterialTheme.colorScheme.primary
                Column(
                    Modifier.background(Color.White).padding(Gap.dp),
                    verticalArrangement = Arrangement.spacedBy(Gap.dp),
                ) {
                    LessonKind.entries.forEach { LessonKindIcon(it) }
                }
            }
        }
        composeRule.waitForIdle()

        val (individual, group) = inkPerKind()

        assertTrue("nothing was drawn: $individual, $group", individual.height > 8 && group.height > 8)
        assertTrue(
            "the person glyph is ${individual.height}px tall and the group glyph ${group.height}px — " +
                "they are not drawn to the same size",
            abs(individual.height - group.height) <= 2,
        )
        assertSameColor("the two kinds are painted differently", individual.color, group.color)
        assertSameColor("the icons are not the brand colour", primary, individual.color)
    }

    private fun inkPerKind(): List<Ink> {
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val inked = { x: Int, y: Int -> distanceFromBackground(pixels[x, y]) > BackgroundTolerance }

        val runs = mutableListOf<IntRange>()
        var start: Int? = null
        for (y in 0 until pixels.height) {
            val hasInk = (0 until pixels.width).any { inked(it, y) }
            if (hasInk && start == null) start = y
            if (!hasInk && start != null) {
                runs += start..(y - 1)
                start = null
            }
        }
        start?.let { runs += it..(pixels.height - 1) }

        val sameGlyph = with(composeRule.density) { (Gap / 2).dp.roundToPx() }
        val bands = mutableListOf<IntRange>()
        runs.forEach { run ->
            val open = bands.lastOrNull()
            if (open != null && run.first - open.last <= sameGlyph) {
                bands[bands.lastIndex] = open.first..run.last
            } else {
                bands += run
            }
        }

        assertEquals(
            "expected one band of rows per kind, got $bands (from runs $runs)",
            LessonKind.entries.size,
            bands.size,
        )
        return bands.map { band -> inkIn(pixels, band, inked) }
    }

    private fun inkIn(
        pixels: PixelMap,
        rows: IntRange,
        inked: (Int, Int) -> Boolean,
    ): Ink {
        var left = Int.MAX_VALUE
        var right = -1
        val counts = mutableMapOf<Color, Int>()
        for (y in rows) {
            for (x in 0 until pixels.width) {
                if (!inked(x, y)) continue
                left = minOf(left, x)
                right = maxOf(right, x)
                val color = pixels[x, y]
                counts[color] = (counts[color] ?: 0) + 1
            }
        }
        val body = counts.keys.maxOf { distanceFromBackground(it) }
        return Ink(
            width = right - left + 1,
            height = rows.last - rows.first + 1,
            color = counts
                .filterKeys { distanceFromBackground(it) >= body - RimTolerance }
                .maxByOrNull { it.value }
                ?.key
                ?: Color.Unspecified,
        )
    }

    private fun assertSameColor(message: String, expected: Color, actual: Color) {
        listOf(
            "red" to (expected.red to actual.red),
            "green" to (expected.green to actual.green),
            "blue" to (expected.blue to actual.blue),
        ).forEach { (channel, values) ->
            assertEquals("$message ($channel): $expected vs $actual", values.first, values.second, 1f / 255f)
        }
    }

    private companion object {
        /** Clear space around and between the icons, so each lands in a band of rows of its own. */
        const val Gap = 24

        /** Anything this close to the white background counts as the background showing through. */
        const val BackgroundTolerance = 0.06f

        /** How far short of the furthest colour a pixel may fall and still count as glyph body. */
        const val RimTolerance = 0.02f

        /** How far a colour sits from the white the icons are drawn on. */
        fun distanceFromBackground(color: Color): Float =
            (1f - color.red) + (1f - color.green) + (1f - color.blue)
    }
}
