package com.seblful.klas.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.seblful.klas.ui.common.Spacing

/*
 * Building blocks shared by the widgets, from the Glance canonical layouts (Android platform samples).
 */

/** A list row: optional leading content, a headline over supporting content, optional trailing content. */
@Composable
internal fun WidgetListItem(
    headlineContent: @Composable () -> Unit,
    modifier: GlanceModifier = GlanceModifier,
    contentSpacing: Dp = Spacing.leading,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: Action? = null,
) {
    Row(
        modifier = if (onClick != null) modifier.clickable(onClick) else modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.let {
            it()
            Spacer(modifier = GlanceModifier.width(contentSpacing))
        }
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            headlineContent()
            supportingContent?.invoke()
        }
    }
}

/** A [LazyColumn] clipped to a rounded rectangle, with [verticalItemsSpacing] between its [items]. */
@Composable
internal fun <T> RoundedScrollingLazyColumn(
    items: List<T>,
    itemId: (T) -> Long,
    itemContentProvider: @Composable (item: T) -> Unit,
    modifier: GlanceModifier = GlanceModifier,
    verticalItemsSpacing: Dp = Spacing.tight,
) {
    Box(modifier = modifier.fillMaxSize().cornerRadius(16.dp)) {
        LazyColumn {
            itemsIndexed(items, itemId = { _, item -> itemId(item) }) { index, item ->
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    itemContentProvider(item)
                    if (index != items.lastIndex) {
                        Spacer(modifier = GlanceModifier.height(verticalItemsSpacing))
                    }
                }
            }
        }
    }
}

/** What a list shows with nothing in it: an icon when there is room, a line of text and a button. */
@Composable
internal fun NoDataContent(
    @DrawableRes noDataIconRes: Int,
    noDataText: String,
    actionButtonText: String,
    @DrawableRes actionButtonIcon: Int?,
    actionButtonOnClick: Action,
) {
    Column(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        if (LocalSize.current.height >= 180.dp) {
            Image(
                provider = ImageProvider(noDataIconRes),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondary),
                contentDescription = null,
            )
            Spacer(modifier = GlanceModifier.height(Spacing.inline))
        }
        Text(
            text = noDataText,
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurface,
                // A narrow widget's line would wrap a lone word.
                fontSize = if (LocalSize.current.width < 260.dp) 14.sp else 16.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier.padding(horizontal = Spacing.itemGap),
        )
        Spacer(modifier = GlanceModifier.height(Spacing.inline))
        FilledButton(
            text = actionButtonText,
            icon = actionButtonIcon?.let(::ImageProvider),
            onClick = actionButtonOnClick,
        )
    }
}
