package com.seblful.klas.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seblful.klas.ui.common.HoldToName
import com.seblful.klas.ui.common.OneLineLabel

/**
 * The app's top-level tabs, as items rather than as a bar.
 *
 * They used to be a `BottomBar` composable wrapping `NavigationBar` directly, which meant the app could
 * only ever *be* a bottom bar: on a tablet or an unfolded foldable it drew a phone's bar across the foot
 * of a ten-inch screen, where the convention is a rail down the side. `NavigationSuiteScaffold` picks
 * between the two from the window's width class, and it wants the tabs as items — so this is the one
 * place a tab is defined, and which chrome renders it is no longer this file's business.
 *
 * @param tabs the tabs to show, in order (see [TopDestination]).
 * @param selectedRoute the tab that is **marked**, which is not the same question as which route is
 *   showing — see `AppRoot`, which reads it off the back stack. This took the *current* route once, and
 *   so answered "none of them" the moment anything was pushed: opening a form dropped every glyph to its
 *   outline and took the pill away, so the bar visibly went dead on its way out. A pushed screen belongs
 *   to the tab it was opened from, and that tab keeps its mark until another one is chosen.
 */
fun NavigationSuiteScope.klasTabs(
    tabs: List<TopDestination>,
    selectedRoute: String?,
    onSelect: (TopDestination) -> Unit,
) {
    tabs.forEach { dest ->
        val selected = selectedRoute == dest.route
        item(
            selected = selected,
            onClick = { onSelect(dest) },
            icon = { TabIcon(dest, selected = selected) },
            label = { TabLabel(dest) },
        )
    }
}

/**
 * A tab's glyph: **outlined when it is not the current tab, filled when it is.**
 *
 * Both used to be the same filled icon, which left the tinted pill behind the selected one as the only
 * thing saying where you were — one signal, and the weakest of the two Material gives you. Filling the
 * selected glyph is what makes the current tab readable at a glance, in a screenshot, and to someone who
 * cannot pick the pill's tint out of the background.
 *
 * **Held, it names its tab** in a bubble above the thumb ([HoldToName]) — the label under it is the
 * smallest type on the screen, and on a narrow phone it has been shrunk to fit.
 */
@Composable
private fun TabIcon(dest: TopDestination, selected: Boolean) {
    HoldToName(stringResource(dest.labelRes), above = true) {
        Icon(
            imageVector = if (selected) dest.icon else dest.iconOutlined,
            // Null: the label beside it says the same word, and `NavigationSuiteScaffold` already builds
            // the item's own selected/unselected semantics. A description here would be read out twice.
            contentDescription = null,
        )
    }
}

/**
 * A tab label on **one line**.
 *
 * With five tabs — «Группы» once, «Задания» now — «Расписание» wants all but a few pixels of its share
 * of a 360dp screen, close enough to the edge that it used to wrap, stranding its last letter on a
 * second line. Holding the label to one line is what fixes that; the type additionally shrinks
 * (never below [MinLabelSize]) on the narrower screens and larger font scales where the word
 * really does not fit, so it stays whole where a fixed size would have to cut it short.
 *
 * Still the bar's problem rather than the rail's — a rail has width to spare — which is why the rule
 * and the test that pins it are named after the bar.
 */
@Composable
private fun TabLabel(dest: TopDestination) {
    OneLineLabel(stringResource(dest.labelRes), floor = MinLabelSize, fallbackSize = FallbackLabelFontSize)
}

/** The smallest a tab label is drawn, on screen — whatever the font scale asked for. */
private val MinLabelSize = 9.dp

/** Material's label size for a navigation bar; used only if the style leaves the size unspecified. */
private val FallbackLabelFontSize = 12.sp
