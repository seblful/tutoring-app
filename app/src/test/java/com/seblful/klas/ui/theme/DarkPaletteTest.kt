package com.seblful.klas.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class DarkPaletteTest {

    @Test
    fun `the dark indigo container is no louder than the lavender accents beside it`() {
        val container = chroma(md_primaryContainer_d)
        val accent = chroma(md_primary_d)
        assertTrue(
            "primaryContainer chroma $container against the accent's $accent — the add button and the income " +
                "card stand out from another palette",
            container <= accent * MaxContainerToAccentChroma,
        )
    }

    @Test
    fun `text on the dark indigo container stays readable`() {
        val ratio = contrast(md_onPrimaryContainer_d, md_primaryContainer_d)
        assertTrue("contrast $ratio", ratio >= WcagAaText)
    }

    /** CIELAB chroma — how colourful, apart from how light. */
    private fun chroma(color: Color): Float = color.convert(ColorSpaces.CieLab).let { hypot(it.green, it.blue) }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private companion object {
        /** The seed-chroma container (#3340AA) was 2.0×; a tone of the accents' own family is about 1.1×. */
        const val MaxContainerToAccentChroma = 1.25f
        const val WcagAaText = 4.5f
    }
}
