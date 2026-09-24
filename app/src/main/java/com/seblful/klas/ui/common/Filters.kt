package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * The app bar's way into narrowing a list to one of [options] — the schedule to a student, «Задания» to
 * a student or a group.
 *
 * A menu rather than a second segmented row or a search box: the answer is one of a list that is as long
 * as the tutor's roster, and it is asked rarely — a filter is something you switch on to check one person
 * and then switch off again. [allLabel] sits at the top as a real entry rather than the absence of one, for
 * the reason `LessonFilter.ALL` does: the way out has to be visible from inside.
 *
 * The icon does **not** change to mark that a filter is on. [FilterChipRow] says that, in words, with the
 * name in it — an icon differing by its fill is a signal a tutor has to already know how to read.
 *
 * @param name what the icon does — its description, and what a hold on it shows ([HoldToName]).
 * @param leading an option's glyph in the menu, where a list mixes kinds (a student, a group).
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
) {
    var open by remember { mutableStateOf(false) }
    Box {
        HoldToName(name) {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Filled.FilterList, name)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = { onSelect(null); open = false },
                trailingIcon = { if (selected == null) Icon(Icons.Filled.Check, null) },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); open = false },
                    leadingIcon = leading?.let { draw -> { draw(option) } },
                    trailingIcon = { if (selected == option) Icon(Icons.Filled.Check, null) },
                )
            }
        }
    }
}

/**
 * Who a list is currently narrowed to, and the one tap that stops.
 *
 * An `InputChip` because that is what this is in Material's vocabulary — a value the tutor put there,
 * removable — rather than a filter chip, which is a toggle among peers. A screen puts it above whatever it
 * narrows and outside the scroll, so it cannot be scrolled away from: the whole point is that it is
 * impossible to be looking at a narrowed list without seeing why.
 */
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
                    Icons.Filled.Close,
                    stringResource(R.string.schedule_filter_clear),
                    Modifier.size(InputChipDefaults.AvatarSize),
                )
            },
        )
    }
}
