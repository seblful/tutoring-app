package com.seblful.klas.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals

class HarmonizeTest {

    @Test
    fun `a hue far from the wallpaper's turns by at most fifteen degrees`() {
        val green = Color(0xFF146C2E)
        val turned = harmonize(green, toward = Color(0xFFB3261E))
        assertEquals(15f, abs(hueDistance(green, turned)), 0.5f)
    }

    @Test
    fun `a hue close to the wallpaper's turns halfway toward it`() {
        val color = Color(0xFF4C5BC4)
        val toward = Color(0xFF6750A4)
        val gap = hueDistance(color, toward)
        assertEquals(gap / 2f, hueDistance(color, harmonize(color, toward)), 0.5f)
    }

    @Test
    fun `lightness is kept so contrast does not move`() {
        val amber = Color(0xFF8B5000)
        val turned = harmonize(amber, toward = Color(0xFF006A60))
        assertEquals(amber.convert(ColorSpaces.CieLab).red, turned.convert(ColorSpaces.CieLab).red, 0.5f)
    }

    @Test
    fun `a colour already on the wallpaper's hue stays put`() {
        val color = Color(0xFF4C5BC4)
        assertEquals(0f, hueDistance(color, harmonize(color, toward = color)), 0.01f)
    }

    /** Signed degrees from [from]'s CIE LCh hue to [to]'s, the short way round. */
    private fun hueDistance(from: Color, to: Color): Float {
        fun hue(c: Color) = c.convert(ColorSpaces.CieLab).let { atan2(it.blue, it.green).toDouble() * 180 / PI }
        return (((hue(to) - hue(from)) % 360 + 540) % 360 - 180).toFloat()
    }
}
