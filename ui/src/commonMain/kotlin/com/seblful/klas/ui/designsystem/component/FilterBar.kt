package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.KlasMenu
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.theme.KlasIcons

/**
 * A list's filters as chips: **at most [MaxChips]**, wrapped and never scrolled sideways — they sit inside
 * pages that swipe sideways — with the rest behind a «Filter» chip that opens them as a ticked menu, and reads
 * as chosen while one of those is (R5).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBar(
    options: List<FilterOption>,
    modifier: Modifier = Modifier,
    /** [MenuChip]s after the toggles — an order, a choice of one among many — counted in the three. */
    menus: @Composable () -> Unit = {},
) {
    val shown = if (options.size > MaxChips) options.take(MaxChips - 1) else options
    val more = options.drop(shown.size)
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        shown.forEach { option ->
            FilterChip(
                selected = option.selected,
                onClick = option.onClick,
                label = { Text(option.label) },
                leadingIcon = option.leading,
            )
        }
        if (more.isNotEmpty()) MoreFilters(more)
        menus()
    }
}

@Composable
private fun MoreFilters(options: List<FilterOption>) {
    val chosen = options.firstOrNull { it.selected }
    // What is chosen in here, by name — a bare «Filter» in the accent would not say what narrowed the list.
    MenuChip(label = chosen?.label ?: stringResource(Strings.filter_more), narrowed = chosen != null) { close ->
        options.forEach { option ->
            TickedMenuItem(text = option.label, selected = option.selected, onClick = {
                close()
                option.onClick()
            })
        }
    }
}

/**
 * A chip that opens a ticked [menu] and is labelled by what is chosen in it — «Everyone ▾», «Аня ▾» — tinted
 * while it narrows the list ([narrowed]). The menu is handed `close`.
 */
@Composable
fun MenuChip(label: String, narrowed: Boolean, menu: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = narrowed,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                Icon(KlasIcons.Filled.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize))
            },
            modifier = Modifier.widthIn(max = MenuChipMaxWidth),
        )
        KlasMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
    }
}

/** A long name ellipsizes rather than pushing the chip beside it off the row. */
private val MenuChipMaxWidth = 180.dp

/** Past three chips a row of them stops being scanned and starts being read. */
const val MaxChips = 3
