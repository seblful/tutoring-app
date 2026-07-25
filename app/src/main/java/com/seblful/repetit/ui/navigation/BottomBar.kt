package com.seblful.repetit.ui.navigation

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse

/**
 * The bottom tab bar.
 *
 * Split out of the screen graph so the bar can be composed — and its labels measured — without a
 * `NavHost` or a view model behind it.
 *
 * @param tabs the tabs to show, in order (see [TopDestination.visible]).
 * @param currentRoute the route currently shown; the matching tab is the selected one, and no tab
 *   is selected for a route that is not a tab.
 */
@Composable
fun BottomBar(
    tabs: List<TopDestination>,
    currentRoute: String?,
    onSelect: (TopDestination) -> Unit,
) {
    NavigationBar {
        tabs.forEach { dest ->
            val label = stringResource(dest.labelRes)
            NavigationBarItem(
                selected = currentRoute == dest.route,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, label) },
                label = { TabLabel(label) },
            )
        }
    }
}

/**
 * A tab label on **one line**.
 *
 * With «Группы» shown there are five tabs, and «Расписание» then wants all but a few pixels of its
 * share of a 360dp screen — close enough to the edge that it used to wrap, stranding its last letter
 * on a second line. Holding the label to one line is what fixes that; the type additionally shrinks
 * (never below [MinLabelFontSize]) on the narrower screens and larger font scales where the word
 * really does not fit, so it stays whole where a fixed size would have to cut it short.
 */
@Composable
private fun TabLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        // Only reachable if even the minimum size overflows (a very large font scale); wrapping is
        // what we are avoiding, so cut the word instead of adding a line.
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MinLabelFontSize,
            // Autosize grows text as readily as it shrinks it, so cap it at the size the bar's own
            // label style asks for — a label that fits must look untouched.
            maxFontSize = LocalTextStyle.current.fontSize.takeOrElse { FallbackLabelFontSize },
        ),
    )
}

private val MinLabelFontSize = 9.sp

/** Material's label size for a navigation bar; used only if the style leaves the size unspecified. */
private val FallbackLabelFontSize = 12.sp
