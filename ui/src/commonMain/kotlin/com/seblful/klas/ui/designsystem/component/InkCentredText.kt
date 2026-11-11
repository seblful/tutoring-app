package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

/**
 * One short line — a number in a disc — placed so its **ink**, not its line box, is centred where it
 * is put.
 *
 * A centred line box is not centred ink: Golos's ascent leaves room above its figures for accents, and
 * each figure has its own side bearings, so a day number sat up to 1.5 px low and left in its 28 dp disc.
 * The ink is measured by the platform, in the face the text resolves to, so it holds for any text.
 */
@Composable
fun InkCentredText(text: String, style: TextStyle, color: ColorProducer, modifier: Modifier = Modifier) {
    val inkBounds = rememberInkBounds(style)
    BasicText(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val ink = if (style.fontSize.isSp) inkBounds(text, style.fontSize.toPx()) else null
            layout(placeable.width, placeable.height) {
                if (ink == null || ink.isEmpty) {
                    placeable.place(0, 0)
                } else {
                    // Ink is measured from the pen's start on the baseline, which is where the line box draws it.
                    val baseline = placeable[FirstBaseline]
                    val dx = placeable.width / 2f - ink.center.x
                    val dy = placeable.height / 2f - (baseline + ink.center.y)
                    placeable.place(dx.roundToInt(), dy.roundToInt())
                }
            }
        },
    )
}

/**
 * Measures where [text]'s ink falls when set in [style] at a font size in pixels, relative to the pen's start
 * on the baseline; null where the platform cannot say, and the line box is placed as it is.
 */
@Composable
internal expect fun rememberInkBounds(style: TextStyle): (text: String, fontSizePx: Float) -> Rect?
