package com.seblful.klas.ui.common

import androidx.compose.animation.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.KlasMotion
import kotlinx.coroutines.launch

/** A header that **stays** while a list scrolls under it — and only looks like a band while it does. */
@Composable
fun PinnedHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Written at placement, so read only off the composition: read by it, a placement that pinned the header
    // recomposed it a frame late, every time the list carried it to the top or away.
    var pinned by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val background = remember { Animatable(colors.surface) }
    LaunchedEffect(colors) {
        snapshotFlow { if (pinned) colors.surfaceContainer else colors.surface }
            .collect { target -> launch { background.animateTo(target, KlasMotion.defaultEffects()) } }
    }
    Column(
        modifier
            .onPlaced { pinned = it.positionInParent().y <= 0f }
            .drawBehind { drawRect(background.value) },
        content = content,
    )
}

/**
 * A detail card's lists as **one pinned control naming the list on screen**, with the others behind it —
 * the student's card, which has four.
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
                Icon(
                    KlasIcons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
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
