package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.seblful.klas.domain.people.PeopleSearch
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.theme.KlasIcons

/** Up to this many options, a menu draws them all; past it, it offers a search. */
const val LONG_MENU = 12

/** How many of a long menu's matches it draws. */
const val MENU_SHOWN = 30

/**
 * A menu's [options], each drawn by [option]. A list that can grow — every student, every group — gets a
 * search past [LONG_MENU], and only its first [MENU_SHOWN] matches are drawn: a dropdown composes every
 * item when it opens, and three hundred names stalled that frame.
 */
@Composable
fun <T> ColumnScope.MenuOptions(
    options: List<T>,
    nameOf: (T) -> String,
    option: @Composable ColumnScope.(T) -> Unit,
) {
    if (options.size <= LONG_MENU) {
        options.forEach { option(it) }
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(options, query) { PeopleSearch.filter(options, query, nameOf) }
    SearchField(query, onQueryChange = { query = it })
    matches.take(MENU_SHOWN).forEach { option(it) }
    val hidden = matches.size - MENU_SHOWN
    if (hidden > 0) {
        MenuItem(
            text = { Text(pluralStringResource(Plurals.menu_more_matches, hidden, hidden)) },
            onClick = {},
            enabled = false,
        )
    }
}

/** The search a long list of choices opens with — a menu's past [LONG_MENU], the currency dialog's. */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(Strings.action_search)) },
        leadingIcon = { Icon(KlasIcons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.inline, vertical = Spacing.tight),
    )
}
