package com.seblful.klas.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider

/** One shortcut in the toolbar. */
internal data class ToolBarButton(
    @param:DrawableRes val iconRes: Int,
    val contentDescription: String,
    val onClick: Action,
)

/** A rounded-rectangle shortcut, its icon centred. */
@Composable
internal fun RectangularIconButton(
    button: ToolBarButton,
    cornerRadius: Dp,
    backgroundColor: ColorProvider,
    contentColor: ColorProvider,
    modifier: GlanceModifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(cornerRadius)
            .semantics { contentDescription = button.contentDescription }
            .clickable(button.onClick),
    ) {
        Image(
            provider = ImageProvider(button.iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = GlanceModifier.size(IconSize),
        )
    }
}

/** A 48dp-tall tap target around a 52x32dp pill, for the title bar. */
@Composable
internal fun PillShapedButton(
    button: ToolBarButton,
    backgroundColor: ColorProvider,
    contentColor: ColorProvider,
    modifier: GlanceModifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics { contentDescription = button.contentDescription }
            .height(MinButtonSize)
            .clickable(button.onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .width(52.dp)
                .height(32.dp)
                .background(backgroundColor)
                .cornerRadius(FullRadius),
        ) {
            Image(
                provider = ImageProvider(button.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = GlanceModifier.size(IconSize),
            )
        }
    }
}

/*
 * The toolbar's dimensions, from the sample's UX design.
 */

/** The smallest accessible tap target. */
internal val MinButtonSize = 48.dp
internal val WidgetPadding = 12.dp
internal val ItemsSpacing = 8.dp
internal val IconSize = 24.dp
internal val FullRadius = 100.dp
internal val MediumRadius = 16.dp

internal val Transparent = ColorProvider(Color.Transparent, Color.Transparent)
