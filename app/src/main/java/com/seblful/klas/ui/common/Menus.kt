package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The parts the app's dropdown menus share: an entry that marks the one in force, and the «⋮» a card keeps
 * its lifecycle actions behind.
 */

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
    DropdownMenuItem(
        text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = { if (selected) Icon(KlasIcons.Filled.Check, contentDescription = null) },
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items { expanded = false }
        }
    }
}

/** Retiring a student or a group, or bringing one back — named by what a tap would do now. */
@Composable
fun ArchiveMenuItem(archived: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
        leadingIcon = { Icon(KlasIcons.Filled.Inventory2, contentDescription = null) },
        onClick = onClick,
    )
}
