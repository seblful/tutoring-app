package com.seblful.klas.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.HoldToName
import com.seblful.klas.ui.common.LocalTabBarClearance
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.OneLineLabel
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.theme.KlasMotion
import kotlin.math.roundToInt

/**
 * The app's top-level tabs as the navigation rail's items. A phone draws them in [DockedTabBar].
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
 * The tabs on a phone: a bar docked at the window's foot, on the page's own colour under a hairline. Each tab
 * is its icon; the marked one grows a pill that names it, and the others make room as it grows.
 *
 * Drawn over the screen rather than beside it, so the screen leaves room for it: [onClearance] reports how
 * much of the window's foot above the navigation bar the bar covers, which `AppRoot` hands each tab as
 * [LocalTabBarClearance]. Measured, because a larger font makes the pill taller.
 *
 * Every tab keeps its label composed, folded to no width while it is not marked, so all five are measured
 * against the same room and each one fits when its tab is marked. A folded label is transparent, which hides it
 * from TalkBack and UiAutomator, so each tab is also named by a content description.
 *
 * @param selected the marked tab — see [KlasTabs].
 */
@Composable
fun DockedTabBar(
    tabs: List<TopDestination>,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
    onClearance: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .onSizeChanged { onClearance(with(density) { it.height.toDp() }) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KlasDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            BoxWithConstraints(
                Modifier
                    .widthIn(max = MaxBarWidth)
                    .fillMaxWidth()
                    .padding(horizontal = BarPadding),
            ) {
                // What the marked tab's label may take: the row, less every tab's icon and the label's own gaps.
                val labelRoom = (maxWidth - TabWidth * tabs.size - LabelGap - LabelEnd).coerceAtLeast(0.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = BarHeight)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { dest ->
                        DockedTab(dest, selected = dest == selected, labelRoom = labelRoom) { onSelect(dest) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockedTab(dest: TopDestination, selected: Boolean, labelRoom: Dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    // How far the tab is marked, 0 to 1 — the spring may overshoot, so whatever takes width clamps it.
    val reveal = animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = KlasMotion.defaultSpatial(),
        label = "tab reveal",
    )
    // On its own clock: a word shown half cut reads as a glitch, so it goes before its room folds and comes
    // once most of its room is there.
    val labelAlpha = animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = if (selected) tween(LabelInMillis, delayMillis = LabelInDelayMillis) else tween(LabelOutMillis),
        label = "tab label alpha",
    )
    // Faded on a short clock of its own: tied to the fold's spring, it hung on as a ghost around the icon.
    val pillAlpha = animateFloatAsState(if (selected) 1f else 0f, tween(PillFadeMillis), label = "tab pill")
    val iconScale by animateFloatAsState(
        if (selected) SelectedIconScale else 1f,
        animationSpec = KlasMotion.bouncySpatial(),
        label = "tab icon scale",
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        KlasMotion.defaultEffects(),
        label = "tab content",
    )
    val shade = stateLayer(interaction)
    val pill = colors.primaryContainer
    Box(
        Modifier
            .heightIn(min = MinTouchTarget)
            .tab(stringResource(dest.labelRes), selected, interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = PillHeight)
                // The tab's own bounds, which grow from the icon's round as the label unfolds: the pill stays
                // around the icon. Scaled about its middle instead, it drifted off the icon as the tab folded.
                .drawBehind {
                    val round = CornerRadius(size.height / 2)
                    val shown = pillAlpha.value
                    if (shown > 0f) drawRoundRect(pill, cornerRadius = round, alpha = shown)
                    if (shade.value > 0f) drawRoundRect(content, cornerRadius = round, alpha = shade.value)
                }
                .padding(horizontal = TabPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides content) {
                Box(
                    Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                ) { TabIcon(dest, selected, labelShown = selected) }
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    Box(Modifier.foldedTo(reveal, labelAlpha, labelRoom).padding(start = LabelGap, end = LabelEnd)) {
                        TabLabel(dest)
                    }
                }
            }
        }
    }
}

/** Selectable as a tab and named by [name] even while its label is folded: transparent, that label goes unread. */
private fun Modifier.tab(
    name: String,
    selected: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = selectable(selected, interaction, indication = null, role = Role.Tab, onClick = onClick)
    .semantics { contentDescription = name }

/**
 * Material's state layer, as an alpha to draw at the tab's bounds as they are now. Not a ripple: a ripple takes
 * its size when the finger lands, on a bare icon, and stayed a grey disc inside the pill that tap had grown.
 */
@Composable
private fun stateLayer(interaction: MutableInteractionSource): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    return animateFloatAsState(
        when {
            pressed || focused -> PressedShade
            hovered -> HoveredShade
            else -> 0f
        },
        KlasMotion.defaultEffects(),
        label = "tab state layer",
    )
}

/**
 * Measured against [room] whatever the row has left, so a label never re-sizes its text while a neighbour's
 * grows; laid out at [reveal] of its width and clipped there, drawn at [alpha]. Placed from its start, so it
 * unfolds from the icon in either direction of reading.
 */
private fun Modifier.foldedTo(reveal: State<Float>, alpha: State<Float>, room: Dp): Modifier = clipToBounds()
    .layout { measurable, constraints ->
        val full = measurable.measure(Constraints(maxWidth = (room + LabelGap + LabelEnd).roundToPx()))
        val width = (full.width * reveal.value.coerceIn(0f, 1f)).roundToInt().coerceAtMost(constraints.maxWidth)
        layout(width, full.height) { full.placeRelative(0, 0) }
    }
    .graphicsLayer { this.alpha = alpha.value }

/** Held, it names its tab — unless [labelShown], where the word is already beside it. */
@Composable
private fun TabIcon(dest: TopDestination, selected: Boolean, labelShown: Boolean = false) {
    val icon = @Composable {
        Icon(
            imageVector = if (selected) dest.icon else dest.iconOutlined,
            // Null: the label beside it says the same word, and the item carries its own selected/unselected
            // semantics. A description here would be read out twice.
            contentDescription = null,
        )
    }
    if (labelShown) icon() else HoldToName(stringResource(dest.labelRes), above = true, content = icon)
}

@Composable
private fun TabLabel(dest: TopDestination) {
    OneLineLabel(
        stringResource(dest.labelRes),
        floor = MinLabelSize,
        fallbackSize = MaterialTheme.typography.labelMedium.fontSize,
    )
}

private val MinLabelSize = 9.sp

/** A landscape phone's tabs stay a thumb's reach apart instead of spreading across the window. */
private val MaxBarWidth = 480.dp

private val BarHeight = 64.dp
private val BarPadding = 8.dp
private val PillHeight = 44.dp
private val TabPadding = 12.dp

/** An unmarked tab: its icon and padding, a touch target wide. */
private val TabWidth = MinTouchTarget
private val LabelGap = 8.dp
private val LabelEnd = 4.dp
private const val SelectedIconScale = 1.1f
private const val LabelInDelayMillis = 110
private const val LabelInMillis = 150
private const val LabelOutMillis = 50
private const val PillFadeMillis = 120
private const val PressedShade = 0.10f
private const val HoveredShade = 0.08f
