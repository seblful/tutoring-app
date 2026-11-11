package com.seblful.klas.ui.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FontLoadResult
import org.jetbrains.skia.Font
import org.jetbrains.skia.Point

/**
 * The glyph outlines' bounds in the face the style resolves to, from Skia, which Compose draws iOS text with;
 * not the font's glyph boxes, which Skia rounds out to whole pixels.
 */
@Composable
internal actual fun rememberInkBounds(style: TextStyle): (text: String, fontSizePx: Float) -> Rect? {
    val fonts = LocalFontFamilyResolver.current
    val typeface = remember(fonts, style.fontFamily, style.fontWeight) {
        (fonts.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value as? FontLoadResult)?.typeface
    }
    return remember(typeface, style.letterSpacing, style.fontSize) {
        { text, fontSizePx -> typeface?.let { inkOf(text, Font(it, fontSizePx), style.letterSpacingPx(fontSizePx)) } }
    }
}

private fun TextStyle.letterSpacingPx(fontSizePx: Float): Float =
    if (letterSpacing.isSp && fontSize.isSp) letterSpacing.value / fontSize.value * fontSizePx else 0f

/** Each glyph's outline, placed at its advance from the pen's start; null when nothing is drawn. */
private fun inkOf(text: String, font: Font, letterSpacingPx: Float): Rect? {
    val glyphs = font.getStringGlyphs(text)
    val positions = font.getPositions(glyphs, Point(0f, 0f))
    return font.getPaths(glyphs).withIndex().mapNotNull { (index, path) ->
        val bounds = path?.computeTightBounds()?.takeUnless { it.isEmpty } ?: return@mapNotNull null
        val x = positions[index].x + index * letterSpacingPx
        val y = positions[index].y
        Rect(bounds.left + x, bounds.top + y, bounds.right + x, bounds.bottom + y)
    }.reduceOrNull { ink, glyph ->
        Rect(
            minOf(ink.left, glyph.left),
            minOf(ink.top, glyph.top),
            maxOf(ink.right, glyph.right),
            maxOf(ink.bottom, glyph.bottom),
        )
    }
}
