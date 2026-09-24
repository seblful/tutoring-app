package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

/**
 * A band that **stays** while a list scrolls under it — so it is drawn as one.
 *
 * The detail cards' tab rows were painted `colorScheme.surface`, which is the same value as the page
 * under them, so a row pinned by `stickyHeader` read as part of the scroll: the rows travelling beneath
 * appeared to slide out from behind nothing, and at the moment the first one reached the band there was
 * no edge for it to disappear behind. The tonal ladder the app already declares has the answer one rung
 * up (`surfaceContainer`, the *section* rung — see `ContentCard`), and a divider at the base draws the
 * edge itself.
 *
 * **What earns it is that content genuinely passes underneath.** «Люди»'s students-or-groups control had
 * this too, on the argument that a fixed band is a fixed band however it came to be fixed — but that
 * control sits *outside* the lists rather than over them, so each list is clipped at its own top edge a
 * few dp below and nothing ever travels under the segments. The band was drawing an edge nothing crosses,
 * at the price of making the app's two segmented controls look like different controls on the two screens
 * most likely to be compared. It is the schedule's plain treatment on both now.
 *
 * So this stays separate from [StickyTabRow] for what it *is* — the band, apart from what is standing in
 * it — rather than for having a second caller.
 */
@Composable
fun PinnedHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        content()
        HorizontalDivider()
    }
}

/** The tab row pinned to the top of a detail card's list — a [PinnedHeader] holding tabs. */
@Composable
fun StickyTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    PinnedHeader(modifier) {
        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            // The row's own container would be `surface` again; the [PinnedHeader] above is what
            // carries the colour, so this stays transparent rather than repainting the page's value
            // over it.
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tabs = tabs,
        )
    }
}

/**
 * A detail card's lists as **one pinned control naming the list on screen**, with the others behind it —
 * the student's card, which has four.
 *
 * Four tabs across a 360dp phone were four words competing for a glance on every visit, when a visit is
 * nearly always about one of them; this shows that one, large, and keeps the rest a tap away. The menu
 * gives each its [count] so nothing is hidden from a tutor who wants to know what is there, and ticks the
 * one being shown.
 *
 * @param count how many rows [option] holds, or null to say nothing.
 */
@Composable
fun <T> StickyViewMenu(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    count: (T) -> Int?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    PinnedHeader(modifier) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.DropdownList) { expanded = true }
                    .heightIn(min = MinTouchTarget)
                    .padding(horizontal = Spacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon(selected), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    label(selected),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.inline),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(count(option)?.let { "${label(option)} · $it" } ?: label(option))
                        },
                        leadingIcon = { Icon(icon(option), contentDescription = null) },
                        trailingIcon = if (option == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = { expanded = false; onSelect(option) },
                    )
                }
            }
        }
    }
}
