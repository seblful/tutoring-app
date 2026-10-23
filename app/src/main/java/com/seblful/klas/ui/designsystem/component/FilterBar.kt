package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
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
fun FilterBar(options: List<FilterOption>, modifier: Modifier = Modifier) {
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
    }
}

@Composable
private fun MoreFilters(options: List<FilterOption>) {
    var open by remember { mutableStateOf(false) }
    val chosen = options.firstOrNull { it.selected }
    Box {
        FilterChip(
            selected = chosen != null,
            onClick = { open = true },
            // What is chosen in here, by name — a bare «Filter» in the accent would not say what narrowed the list.
            label = { Text(chosen?.label ?: stringResource(R.string.filter_more)) },
            trailingIcon = {
                Icon(KlasIcons.Filled.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize))
            },
        )
        KlasMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                TickedMenuItem(text = option.label, selected = option.selected, onClick = {
                    open = false
                    option.onClick()
                })
            }
        }
    }
}

/** Past three chips a row of them stops being scanned and starts being read. */
const val MaxChips = 3
