package com.seblful.klas.ui.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextStyle

/** Not measured on iOS yet: the line box is centred, a pixel or so off the ink in Golos's figures. */
@Composable
internal actual fun rememberInkBounds(style: TextStyle): (text: String, fontSizePx: Float) -> Rect? = { _, _ -> null }
