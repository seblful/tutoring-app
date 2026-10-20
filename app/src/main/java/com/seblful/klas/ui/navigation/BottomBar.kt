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
 * @param tabs the tabs to show, in order (see [TopDestination]).
 * @param selected the tab that is **marked**, which is not the same question as which route is
 * showing — see `AppRoot`, which reads it off the back stack. This took the *current* route once, and
 * so answered "none of them" the moment anything was pushed: opening a form dropped every glyph to its
 * outline and took the pill away, so the bar visibly went dead on its way out. A pushed screen belongs
 * to the tab it was opened from, and that tab keeps its mark until another one is chosen.
 */
fun NavigationSuiteScope.klasTabs(
    tabs: List<TopDestination>,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
) {
    tabs.forEach { dest ->
        val isSelected = selected == dest
        item(
            selected = isSelected,
            onClick = { onSelect(dest) },
            icon = { TabIcon(dest, selected = isSelected) },
            label = { TabLabel(dest) },
        )
    }
}

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

@Composable
private fun TabLabel(dest: TopDestination) {
    OneLineLabel(stringResource(dest.labelRes), floor = MinLabelSize, fallbackSize = FallbackLabelFontSize)
}

private val MinLabelSize = 9.dp

private val FallbackLabelFontSize = 12.sp
