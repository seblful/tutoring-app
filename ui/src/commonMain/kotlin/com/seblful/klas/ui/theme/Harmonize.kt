package com.seblful.klas.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * [color] with its hue turned toward [toward]'s by half their difference, at most 15° — Material's
 * `Blend.harmonize` — so the app's own colours sit with a wallpaper scheme yet keep their meaning.
 *
 * Done in CIE LCh rather than HCT, which Compose does not ship: lightness is kept exactly, so contrast
 * against the scheme's surfaces does not move.
 */
fun harmonize(color: Color, toward: Color): Color {
    val from = color.convert(ColorSpaces.CieLab)
    val to = toward.convert(ColorSpaces.CieLab)
    val fromHue = hueDegrees(from)
    val difference = ((hueDegrees(to) - fromHue) % 360f + 540f) % 360f - 180f
    val hue = (fromHue + min(abs(difference) / 2f, MaxRotation) * sign(difference)).toDouble() * PI / HALF_TURN
    val chroma = hypot(from.green, from.blue)
    return Color(
        from.red,
        chroma * cos(hue).toFloat(),
        chroma * sin(hue).toFloat(),
        color.alpha,
        ColorSpaces.CieLab,
    ).convert(ColorSpaces.Srgb)
}

private fun hueDegrees(lab: Color): Float = (atan2(lab.blue, lab.green).toDouble() * HALF_TURN / PI).toFloat()

private const val HALF_TURN = 180.0

private fun sign(value: Float): Float = if (value < 0f) -1f else 1f

private const val MaxRotation = 15f
