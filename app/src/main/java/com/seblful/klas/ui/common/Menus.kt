package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The parts the app's dropdown menus share: the menu's look, its rows, an entry that marks the one in force,
 * and the «⋮» a card keeps its lifecycle actions behind.
 */

/*
 * How every menu looks, a form field's included (`ExposedDropdownMenu` takes these too): Material 3
 * Expressive's rounded container in place of 1.4's near-square one, with rows rounded inside it ([MenuItem]).
 */
internal val MenuShape: Shape @Composable get() = MaterialTheme.shapes.large
internal val MenuContainerColor: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
internal val MenuShadowElevation = 2.dp

/** A dropdown menu in the app's look: [MenuShape], [MenuContainerColor], [MenuShadowElevation]. */
@Composable
fun KlasMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = MenuShape,
        containerColor = MenuContainerColor,
        shadowElevation = MenuShadowElevation,
        content = content,
    )
}

/**
 * A menu row: pressed within a rounded shape inset from the menu's sides, never a band across it, and —
 * when [selected] — filled, so the choice in force reads at a glance and not only by its tick.
 */
@Composable
fun MenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val fill = MaterialTheme.colorScheme.secondaryContainer
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = if (selected) {
            MenuDefaults.itemColors(textColor = ink, leadingIconColor = ink, trailingIconColor = ink)
        } else {
            MenuDefaults.itemColors()
        },
        modifier = modifier
            .padding(horizontal = MenuItemInset)
            .clip(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.background(fill) else Modifier),
    )
}

/**
 * A menu entry that may be **the one in force** — a sort order, a report's period, a filter, a card's list.
 *
 * @param leadingIcon an entry's glyph, where a menu mixes kinds (a student, a group) or names views.
 */
@Composable
fun TickedMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    MenuItem(
        text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = { if (selected) Icon(KlasIcons.Filled.Check, contentDescription = null) },
        selected = selected,
        modifier = Modifier.semantics { this.selected = selected },
    )
}

/**
 * «⋮» in a card's bar, and the menu it opens — where a student's and a group's lifecycle actions sit, one
 * step back from the pencil rather than beside it.
 *
 * @param items the entries, given the `close` that shuts the menu: an entry that acted and left the menu
 * open would leave the tutor tapping beside it to get rid of it.
 */
@Composable
fun OverflowMenu(items: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NamedIconButton(KlasIcons.Filled.MoreVert, stringResource(R.string.action_more), onClick = { expanded = true })
        KlasMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items { expanded = false }
        }
    }
}

/** A part of a menu, named over its entries: «Status» over the statuses a list can be narrowed to. */
@Composable
fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = MenuHeadingStart, end = MenuHeadingStart, top = 12.dp, bottom = 4.dp),
    )
}

/** Retiring a student or a group, or bringing one back — named by what a tap would do now. */
@Composable
fun ArchiveMenuItem(archived: Boolean, onClick: () -> Unit) {
    MenuItem(
        text = { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
        leadingIcon = { Icon(KlasIcons.Outlined.Inventory2, contentDescription = null) },
        onClick = onClick,
    )
}

private val MenuItemInset = 4.dp

/** Where a menu row's words start: Material's 12 dp inside the row, and the row's own inset. */
private val MenuHeadingStart = 16.dp
