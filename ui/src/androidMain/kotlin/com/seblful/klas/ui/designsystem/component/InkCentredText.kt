package com.seblful.klas.ui.designsystem.component

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    val fonts = LocalFontFamilyResolver.current
    val typeface = remember(fonts, style.fontFamily, style.fontWeight) {
        fonts.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value as? Typeface
    }
    BasicText(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val ink = RectF()
            val measured = typeface != null && style.fontSize.isSp
            if (measured) {
                // The outline's bounds, not `getTextBounds`: that rounds to whole pixels, a pixel off at 1×.
                val outline = Path()
                Paint().apply {
                    this.typeface = typeface
                    textSize = style.fontSize.toPx()
                    if (style.letterSpacing.isSp) letterSpacing = style.letterSpacing.value / style.fontSize.value
                }.getTextPath(text, 0, text.length, 0f, 0f, outline)
                outline.computeBounds(ink, true)
            }
            layout(placeable.width, placeable.height) {
                if (!measured || ink.isEmpty) {
                    placeable.place(0, 0)
                } else {
                    // Ink is measured from the pen's start on the baseline, which is where the line box draws it.
                    val baseline = placeable[FirstBaseline]
                    val dx = placeable.width / 2f - ink.centerX()
                    val dy = placeable.height / 2f - (baseline + ink.centerY())
                    placeable.place(dx.roundToInt(), dy.roundToInt())
                }
            }
        },
    )
}
