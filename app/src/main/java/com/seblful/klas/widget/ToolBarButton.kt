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
import androidx.glance.layout.Column
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.Spacing

/** One shortcut in the toolbar. */
internal data class ToolBarButton(
    @param:DrawableRes val iconRes: Int,
    val contentDescription: String,
    val onClick: Action,
)

/** A rounded-rectangle shortcut, its icon centred, and its name under it when [labelled]. */
@Composable
internal fun RectangularIconButton(
    button: ToolBarButton,
    cornerRadius: Dp,
    backgroundColor: ColorProvider,
    contentColor: ColorProvider,
    modifier: GlanceModifier,
    labelled: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(cornerRadius)
            .semantics { contentDescription = button.contentDescription }
            .clickable(button.onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                provider = ImageProvider(button.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = GlanceModifier.size(IconSize),
            )
            if (labelled) {
                Text(
                    text = button.contentDescription,
                    maxLines = 2,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = WidgetLabel,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier.padding(start = Spacing.tight, top = Spacing.tight, end = Spacing.tight),
                )
            }
        }
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
                .width(PillWidth)
                .height(PillHeight)
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

/** Glance's title bar, which the toolbar's largest layout sits under. */
internal val TitleBarHeight = 56.dp

/** A tile with room for its icon and a two-line name. */
internal val LabelledTileHeight = 72.dp

/** The smallest accessible tap target. */
// The app's own spacing and touch size (`Spacing`, `MinTouchTarget`): the widget keeps sp text and the system
// font, which Glance needs, but not numbers of its own.
internal val MinButtonSize = MinTouchTarget
internal val WidgetPadding = Spacing.itemGap
internal val ItemsSpacing = Spacing.inline
internal val IconSize = 24.dp
internal val FullRadius = 100.dp
internal val MediumRadius = 16.dp

internal val Transparent = ColorProvider(Color.Transparent, Color.Transparent)

/** The pill that lights behind a button's icon. */
private val PillWidth = 52.dp
private val PillHeight = 32.dp
