package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
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
    var open by remember { mutableStateOf(false) }
    Box {
        NamedIconButton(KlasIcons.Filled.FilterList, name, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TickedMenuItem(
                text = allLabel,
                selected = !narrowed,
                onClick = { onSelect(null); open = false },
            )
            options.forEach { option ->
                TickedMenuItem(
                    text = optionLabel(option),
                    selected = selected == option,
                    onClick = { onSelect(option); open = false },
                    leadingIcon = leading?.let { draw -> { draw(option) } },
                )
            }
        }
    }
}

/** ho a list is currently narrowed to, and the one tap that stops. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipRow(name: String, onClear: () -> Unit) {
    Row(
        Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputChip(
            selected = true,
            onClick = onClear,
            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                Icon(
                    KlasIcons.Filled.Close,
                    stringResource(R.string.schedule_filter_clear),
                    Modifier.size(InputChipDefaults.IconSize),
                )
            },
        )
    }
}
