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
import com.seblful.klas.domain.LessonKind
import com.seblful.klas.ui.theme.KlasTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * The two lesson-kind icons stand side by side down every list of lessons, so they have to read as one
 * pair: same colour, same size. Neither followed from merely asking for both. `Person` fills 16 of its
 * 24-unit viewport vertically and `Groups` only 12, so at one nominal size the person came out a third
 * taller — 40px against 30px — and the individual icon was additionally tinted grey against the group's
 * brand colour, which made the pair read as a ranking rather than as two kinds.
 *
 * Measured off the drawn pixels, not the declared modifiers: the declared size is precisely what was
 * already equal while the icons plainly were not. The two icons are drawn in **one** composition and
 * read out of **one** capture — a screenshot costs a round trip to the host window, and doing it twice
 * per test made this flaky when the activity went away between the two.
 */
@HiltAndroidTest
class LessonKindIconTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    /** What was actually painted for one kind: the glyph's bounding box, and the colour of its body. */
    private data class Ink(val width: Int, val height: Int, val color: Color)

    private var primary: Color = Color.Unspecified

    @Test
    fun bothKindsAreDrawnToTheSameHeightInTheSameColour() {
        composeRule.setContent {
            KlasTheme {
                primary = MaterialTheme.colorScheme.primary
                // White behind them so "not the background" is a sound test for "ink", and a gap wider
                // than either glyph so the two never merge into one band of rows.
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
        // A pixel either way is antialiasing, not a difference in size.
        assertTrue(
            "the person glyph is ${individual.height}px tall and the group glyph ${group.height}px — " +
                "they are not drawn to the same size",
            abs(individual.height - group.height) <= 2,
        )
        assertSameColor("the two kinds are painted differently", individual.color, group.color)
        assertSameColor("the icons are not the brand colour", primary, individual.color)
    }

    /**
     * The glyphs in the order [LessonKind.entries] drew them, read out of a single screenshot: rows
     * holding any ink, grouped into one band per icon.
     *
     * A glyph is not one unbroken run of rows — `Person` leaves clear pixels between the head and the
     * shoulders, and would otherwise count as two icons. So runs closer together than half the gap the
     * column puts between the icons belong to the same glyph, which is the whole point of making that gap
     * wider than either of them.
     */
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
        // Whichever colour sits furthest from the background is the glyph's body; every value between it
        // and the background is the antialiased rim, which is more numerous than any single body shade
        // and so would win a plain vote. Relative rather than a fixed threshold, because the tint is
        // light on a dark theme and dark on a light one.
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

    /** Equal to within one step of 8-bit colour, which is all a round trip through a bitmap promises. */
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
