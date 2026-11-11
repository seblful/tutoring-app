package com.seblful.klas.ui.designsystem.component

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** The outline's bounds in the face the style resolves to; not `getTextBounds`, which rounds to whole pixels. */
@Composable
internal actual fun rememberInkBounds(style: TextStyle): (text: String, fontSizePx: Float) -> Rect? {
    val fonts = LocalFontFamilyResolver.current
    val typeface = remember(fonts, style.fontFamily, style.fontWeight) {
        fonts.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value as? Typeface
    }
    return remember(typeface, style.letterSpacing, style.fontSize) {
        { text, fontSizePx ->
            typeface?.let {
                val outline = Path()
                Paint().apply {
                    this.typeface = it
                    textSize = fontSizePx
                    if (style.letterSpacing.isSp) letterSpacing = style.letterSpacing.value / style.fontSize.value
                }.getTextPath(text, 0, text.length, 0f, 0f, outline)
                val ink = RectF()
                outline.computeBounds(ink, true)
                Rect(ink.left, ink.top, ink.right, ink.bottom)
            }
        }
    }
}
