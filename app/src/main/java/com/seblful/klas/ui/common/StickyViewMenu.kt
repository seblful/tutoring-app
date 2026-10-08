package com.seblful.klas.ui.common

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import com.seblful.klas.format.bulletJoin

/**
 * A header that **stays** while a list scrolls under it — and only looks like a band while it does.
 *
 * It used to be a band from the start: a full-width `surfaceContainer` strip with a divider under it,
 * drawn the moment the card opened. Everything else on a detail card is an inset, rounded shape on the
 * gutter, so the one edge-to-edge grey bar read as a toolbar wedged between the stat row and the list —
 * an edge drawn for content that, on opening, was not passing under it at all.
 *
 * So at rest it is part of the page: the page's own colour, no rule, its control on the gutter like the
 * cards above it. Once it is **pinned** — at the top of the list, rows travelling beneath — it takes the
 * colour the collapsed app bar has by then (`surfaceContainer`, Material's scrolled bar colour), so the
 * bar and the header become one block with one lower edge for the rows to disappear behind. That edge is
 * the thing the band existed to provide, and now it is there exactly when something crosses it.
 *
 * Pinned is read off where the header was placed: a sticky header sits at the very top of the list's
 * viewport while it is held there, and lower down while it is not.
 */
@Composable
fun PinnedHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var pinned by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (pinned) colors.surfaceContainer else colors.surface,
        label = "pinned header",
    )
    Column(
        modifier
            .onPlaced { pinned = it.positionInParent().y <= 0f }
            .drawBehind { drawRect(background) },
        content = content,
    )
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
                    .insetRowPress {
                        Modifier.clickable(it, indication = null, role = Role.DropdownList) { expanded = true }
                    }
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
                    TickedMenuItem(
                        text = count(option)?.let { bulletJoin(label(option), it.toString()) } ?: label(option),
                        selected = option == selected,
                        onClick = { expanded = false; onSelect(option) },
                        leadingIcon = { Icon(icon(option), contentDescription = null) },
                    )
                }
            }
        }
    }
}
