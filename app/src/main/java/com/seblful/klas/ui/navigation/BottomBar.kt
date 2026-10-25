package com.seblful.klas.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.HoldToName
import com.seblful.klas.ui.common.LocalTabBarClearance
import com.seblful.klas.ui.common.OneLineLabel
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.theme.KlasMotion

/**
 * The app's top-level tabs as the navigation rail's items. A phone draws them in [FloatingTabBar].
 *
 * @param tabs the tabs to show, in order (see [TopDestination]).
 * @param selected the tab that is **marked**, which is not the same question as which route is
 * showing — see `AppRoot`, which reads it off the back stack. This took the *current* route once, and
 * so answered "none of them" the moment anything was pushed: opening a form dropped every glyph to its
 * outline and took the pill away, so the bar visibly went dead on its way out. A pushed screen belongs
 * to the tab it was opened from, and that tab keeps its mark until another one is chosen.
 */
@Composable
fun KlasTabs(
    tabs: List<TopDestination>,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
) {
    tabs.forEach { dest ->
        val isSelected = selected == dest
        NavigationSuiteItem(
            selected = isSelected,
            onClick = { onSelect(dest) },
            icon = { TabIcon(dest, selected = isSelected) },
            label = { TabLabel(dest) },
        )
    }
}

/**
 * The tabs on a phone: a pill floating over the foot of the screen, so a tab's rows run on beneath it.
 *
 * Drawn over the screen rather than beside it, so the screen leaves room for it: [onClearance] reports how
 * much of the window's foot above the navigation bar the pill covers, which `AppRoot` hands each tab as
 * [LocalTabBarClearance]. Measured, because a larger font makes the bar taller. The marked tab's indicator
 * slides from tab to tab instead of appearing in place.
 *
 * @param selected the marked tab — see [KlasTabs].
 */
@Composable
fun FloatingTabBar(
    tabs: List<TopDestination>,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
    onClearance: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = BarGap),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = MaxBarWidth)
                .fillMaxWidth()
                .onSizeChanged { onClearance(with(density) { it.height.toDp() } + BarGap) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = BarElevation,
        ) {
            FloatingTabItems(tabs, selected, onSelect)
        }
    }
}

@Composable
private fun FloatingTabItems(
    tabs: List<TopDestination>,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
) {
    val colors = ShortNavigationBarItemDefaults.colors()
    val index = tabs.indexOf(selected)
    val position by animateFloatAsState(
        index.coerceAtLeast(0).toFloat(),
        animationSpec = KlasMotion.defaultSpatial(),
        label = "tab indicator",
    )
    Row(
        Modifier
            .selectableGroup()
            .padding(horizontal = BarPadding)
            // One pill under the row, read at draw time: the spring moves it without recomposing a tab.
            .drawBehind {
                if (index < 0) return@drawBehind
                val slot = size.width / tabs.size
                val width = IndicatorWidth.toPx()
                val start = position * slot + (slot - width) / 2
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - start - width else start
                drawRoundRect(
                    color = colors.selectedIndicatorColor,
                    topLeft = Offset(x, ItemPadding.toPx()),
                    size = Size(width, IndicatorHeight.toPx()),
                    cornerRadius = CornerRadius(IndicatorHeight.toPx() / 2),
                )
            },
    ) {
        tabs.forEach { dest ->
            val isSelected = dest == selected
            FloatingTab(
                dest = dest,
                selected = isSelected,
                iconColor = if (isSelected) colors.selectedIconColor else colors.unselectedIconColor,
                labelColor = if (isSelected) colors.selectedTextColor else colors.unselectedTextColor,
                onClick = { onSelect(dest) },
            )
        }
    }
}

@Composable
private fun RowScope.FloatingTab(
    dest: TopDestination,
    selected: Boolean,
    iconColor: Color,
    labelColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val icon by animateColorAsState(iconColor, KlasMotion.defaultEffects(), label = "tab icon")
    val label by animateColorAsState(labelColor, KlasMotion.defaultEffects(), label = "tab label")
    Column(
        Modifier
            .weight(1f)
            // The ripple is the indicator's shape, as Material's own items draw it, not the whole cell.
            .selectable(selected, interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(vertical = ItemPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IconToLabel),
    ) {
        Box(
            Modifier
                .size(IndicatorWidth, IndicatorHeight)
                .clip(CircleShape)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides icon) { TabIcon(dest, selected) }
        }
        CompositionLocalProvider(LocalContentColor provides label) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) { TabLabel(dest) }
        }
    }
}

@Composable
private fun TabIcon(dest: TopDestination, selected: Boolean) {
    HoldToName(stringResource(dest.labelRes), above = true) {
        Icon(
            imageVector = if (selected) dest.icon else dest.iconOutlined,
            // Null: the label beside it says the same word, and the item carries its own selected/unselected
            // semantics. A description here would be read out twice.
            contentDescription = null,
        )
    }
}

@Composable
private fun TabLabel(dest: TopDestination) {
    OneLineLabel(
        stringResource(dest.labelRes),
        floor = MinLabelSize,
        fallbackSize = MaterialTheme.typography.labelMedium.fontSize,
    )
}

private val MinLabelSize = 9.dp

/** Between the pill and the navigation bar's inset — or the window's foot, where there is none. */
private val BarGap = 8.dp

/** A landscape phone's pill stays a pill instead of a band across the window. */
private val MaxBarWidth = 480.dp

/** Material's level 3, as its floating toolbars carry. */
private val BarElevation = 6.dp

private val BarPadding = 4.dp
private val ItemPadding = 6.dp
private val IconToLabel = 4.dp
private val IndicatorWidth = 56.dp
private val IndicatorHeight = 32.dp
