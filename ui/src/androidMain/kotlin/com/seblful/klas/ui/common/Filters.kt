package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.theme.KlasIcons

/**
 * The app bar's way into narrowing a list to one of [options] — the schedule to a student, «Задания» to
 * a student or a group.
 *
 * @param name what the icon does — its description, and what a hold on it shows ([HoldToName]).
 * @param leading an option's glyph in the menu, where a list mixes kinds (a student, a group).
 * @param narrowed whether the list is narrowed at all — true even when the one it is narrowed to is not an
 * option (an archived student the schedule was already showing), so «Все» is not ticked under a chip
 * that names somebody.
 */
@Composable
fun <T> FilterMenuAction(
    name: String,
    allLabel: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    leading: (@Composable (T) -> Unit)? = null,
    narrowed: Boolean = selected != null,
) {
    FilterMenu(name) { close ->
        TickedMenuItem(
            text = allLabel,
            selected = !narrowed,
            onClick = { onSelect(null); close() },
        )
        MenuOptions(options, optionLabel) { option ->
            TickedMenuItem(
                text = optionLabel(option),
                selected = selected == option,
                onClick = { onSelect(option); close() },
                leadingIcon = leading?.let { draw -> { draw(option) } },
            )
        }
    }
}

/**
 * The app bar's filter icon and the menu it opens, for a list narrowed more than one way — parts under
 * [MenuHeading]s. What is in force shows under the bar as [ActiveFilters], never as a row of controls that
 * takes the list's room whether anything is narrowed or not.
 *
 * @param items the menu's entries, given the `close` that shuts it.
 */
@Composable
fun FilterMenu(name: String, items: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        NamedIconButton(KlasIcons.Filled.FilterList, name, onClick = { open = true })
        KlasMenu(expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}

/** A narrowing in force: what its chip says, and the tap that takes it off. */
data class ActiveFilter(val name: String, val onClear: () -> Unit)

/**
 * The narrowings in force, a chip each, which say what is hidden and are the way back: a narrowed list and
 * a short one look alike. None in force, none drawn — the list keeps the room.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFilters(filters: List<ActiveFilter>, modifier: Modifier = Modifier) {
    if (filters.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        filters.forEach { filter -> ActiveFilterChip(filter) }
    }
}

/** Who a list is currently narrowed to, and the one tap that stops. */
@Composable
fun FilterChipRow(name: String, onClear: () -> Unit) {
    ActiveFilters(
        listOf(ActiveFilter(name, onClear)),
        Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.tight),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChip(filter: ActiveFilter) {
    InputChip(
        selected = true,
        onClick = filter.onClear,
        label = { Text(filter.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
            Icon(
                KlasIcons.Filled.Close,
                stringResource(Strings.filter_remove),
                Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}
