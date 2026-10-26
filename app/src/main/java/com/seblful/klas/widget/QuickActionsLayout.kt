package com.seblful.klas.widget

import androidx.compose.runtime.Composable
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.seblful.klas.MainActivity
import com.seblful.klas.R

/**
 * The Glance canonical toolbar layout (Android platform samples): the app's mark, which opens the app, one
 * prominent shortcut ([headerButton]) and four more ([buttons]), arranged by the space the widget has.
 */
@Composable
internal fun QuickActionsLayout(
    appName: String,
    openApp: Action,
    headerButton: ToolBarButton,
    buttons: List<ToolBarButton>,
) {
    val appIconItem: @Composable () -> Unit = { FluidHeaderAppIcon(appName, openApp) }
    val headerButtonItem: @Composable () -> Unit = { FluidHeaderIconButton(headerButton) }
    val labelled = canLabelGridButtons()
    val buttonItems: List<@Composable () -> Unit> =
        buttons.map { { FluidContentIconButton(it, filled = canUseFilledButtons(), labelled = labelled) } }

    when (val layoutSize = ToolBarLayoutSize.fromLocalSize()) {
        ToolBarLayoutSize.HorizontalRow, ToolBarLayoutSize.VerticalColumn -> {
            Scaffold(modifier = GlanceModifier.padding(vertical = WidgetPadding), horizontalPadding = WidgetPadding) {
                val horizontal = layoutSize == ToolBarLayoutSize.HorizontalRow
                val fit = numberOfItemsThatFit(horizontal)
                // Room for one: the prominent shortcut, not a mark that only opens the app.
                val items = if (fit <= 1) {
                    listOf<@Composable () -> Unit>({ FluidHeaderIconButton(headerButton, filled = true) })
                } else {
                    (listOf(appIconItem, headerButtonItem) + buttonItems).take(fit)
                }
                if (horizontal) {
                    SpacedRow(items, modifier = GlanceModifier.fillMaxSize())
                } else {
                    SpacedColumn(items, modifier = GlanceModifier.fillMaxSize())
                }
            }
        }
        ToolBarLayoutSize.TwoRowGrid -> {
            // Beside the mark and the prominent shortcut: two more columns where they fit, else one.
            val contentButtons = buttonItems.take(if (LocalSize.current.width >= ToolBarWideMinWidth) 4 else 2)
            Scaffold(modifier = GlanceModifier.padding(vertical = WidgetPadding), horizontalPadding = WidgetPadding) {
                TwoRowGrid(listOf(appIconItem, headerButtonItem) + contentButtons)
            }
        }
        ToolBarLayoutSize.HeaderTwoRowGrid -> {
            Scaffold(
                modifier = GlanceModifier.padding(bottom = WidgetPadding),
                horizontalPadding = WidgetPadding,
                titleBar = { Header(title = if (canShowHeaderTitle()) appName else "", actionButton = headerButton) },
            ) {
                TwoRowGrid(buttonItems)
            }
        }
    }
}

/** The title bar at larger sizes: the mark, the title and the prominent shortcut as a pill. */
@Composable
private fun Header(title: String, actionButton: ToolBarButton) {
    TitleBar(
        startIcon = ImageProvider(R.drawable.ic_logo),
        title = title,
        iconColor = GlanceTheme.colors.primary,
        actions = {
            PillShapedButton(
                button = actionButton,
                backgroundColor = if (canUseFilledButtons()) GlanceTheme.colors.tertiary else Transparent,
                contentColor = GlanceTheme.colors.onTertiary,
                modifier = GlanceModifier.padding(end = WidgetPadding),
            )
        },
    )
}

/** The mark where no title bar fits, sized and spaced like a button, and like a launcher icon opens the app. */
@Composable
private fun FluidHeaderAppIcon(appName: String, openApp: Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(FullRadius)
            .semantics { contentDescription = appName }
            .clickable(openApp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_logo),
            contentDescription = null,
            modifier = GlanceModifier.size(IconSize),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
    }
}

/** The prominent shortcut where no title bar fits: fully rounded, beside the others, and filled where it fits. */
@Composable
private fun FluidHeaderIconButton(button: ToolBarButton, filled: Boolean = canUseFilledButtons()) {
    RectangularIconButton(
        button = button,
        cornerRadius = FullRadius,
        backgroundColor = if (filled) GlanceTheme.colors.tertiary else Transparent,
        contentColor = if (filled) GlanceTheme.colors.onTertiary else GlanceTheme.colors.onSecondaryContainer,
        modifier = GlanceModifier.fillMaxSize(),
    )
}

@Composable
private fun FluidContentIconButton(button: ToolBarButton, filled: Boolean, labelled: Boolean) {
    RectangularIconButton(
        button = button,
        labelled = labelled,
        cornerRadius = MediumRadius,
        backgroundColor = if (filled) GlanceTheme.colors.secondaryContainer else Transparent,
        contentColor = GlanceTheme.colors.onSecondaryContainer,
        modifier = GlanceModifier.fillMaxSize(),
    )
}

/** Breakpoints from the sample's UX design. */
private enum class ToolBarLayoutSize {
    /** The mark, the prominent shortcut and as many others as fit across. */
    HorizontalRow,

    /** The same, down. */
    VerticalColumn,

    /** The mark over the prominent shortcut, then one or two columns of the others. */
    TwoRowGrid,

    /** A title bar holding the mark and the prominent shortcut, over the other four in two rows. */
    HeaderTwoRowGrid,
    ;

    companion object {
        @Composable
        fun fromLocalSize(): ToolBarLayoutSize {
            val size = LocalSize.current
            return when {
                size.height < ToolBarRowMaxHeight -> HorizontalRow
                size.width < ToolBarColumnMaxWidth -> VerticalColumn
                size.height < ToolBarHeaderMinHeight -> TwoRowGrid
                else -> HeaderTwoRowGrid
            }
        }
    }
}

/** Filled buttons only where neither side is cramped. */
@Composable
private fun canUseFilledButtons(): Boolean = LocalSize.current.let {
    it.height >= ToolBarFilledMinSize && it.width >= ToolBarFilledMinSize
}

/**
 * Names under the icons where the title bar's grid has tiles tall enough for a name on two lines: four icons
 * alone are a guessing game.
 */
@Composable
private fun canLabelGridButtons(): Boolean {
    if (ToolBarLayoutSize.fromLocalSize() != ToolBarLayoutSize.HeaderTwoRowGrid) return false
    val tileHeight = (LocalSize.current.height - TitleBarHeight - WidgetPadding - ItemsSpacing) / 2
    return tileHeight >= LabelledTileHeight
}

@Composable
private fun canShowHeaderTitle(): Boolean = LocalSize.current.let {
    it.width >= ToolBarWideMinWidth && it.height >= ToolBarHeaderMinHeight
}

/** How many [MinButtonSize] items fit along the row or column, [ItemsSpacing] apart. */
@Composable
private fun numberOfItemsThatFit(horizontal: Boolean): Int {
    val length = if (horizontal) LocalSize.current.width else LocalSize.current.height
    // n items have n - 1 gaps; one extra gap on each side of the division makes them even.
    return ((length + ItemsSpacing) / (MinButtonSize + ItemsSpacing)).toInt()
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 56, heightDp = 56)
@Preview(widthDp = 128, heightDp = 48)
@Preview(widthDp = 296, heightDp = 72)
@Preview(widthDp = 72, heightDp = 228)
@Preview(widthDp = 128, heightDp = 128)
@Preview(widthDp = 296, heightDp = 128)
@Preview(widthDp = 240, heightDp = 228)
@Composable
private fun QuickActionsPreview() {
    val action = actionStartActivity<MainActivity>()
    GlanceTheme(colors = KlasWidgetColors) {
        QuickActionsLayout(
            appName = "Klas",
            openApp = action,
            headerButton = ToolBarButton(R.drawable.widget_icon_add, "Book a lesson", action),
            buttons = listOf(
                ToolBarButton(R.drawable.widget_icon_today, "Today", action),
                ToolBarButton(R.drawable.widget_icon_people, "People", action),
                ToolBarButton(R.drawable.widget_icon_homework, "Homework", action),
                ToolBarButton(R.drawable.widget_icon_debts, "Debts", action),
            ),
        )
    }
}
