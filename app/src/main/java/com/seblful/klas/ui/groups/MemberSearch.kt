package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.PeopleSearch
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.theme.KlasIcons

/** Narrows a long roster by name — forty students is a long scroll to find the four in a group. */
@Composable
internal fun MemberSearch(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.students_search_hint)) },
        leadingIcon = { Icon(KlasIcons.Filled.Search, contentDescription = null) },
        // Cleared with ✕, as every other search is.
        trailingIcon = {
            if (query.isNotEmpty()) {
                NamedIconButton(
                    KlasIcons.Filled.Close,
                    stringResource(R.string.search_clear),
                    onClick = { onQuery("") },
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** How many students a roster holds before it can be searched. */
internal const val MemberSearchFrom = 8

/** The students whose name holds every word of [query], in any order, as People's search finds them. */
internal fun List<Student>.matching(query: String): List<Student> = PeopleSearch.filter(this, query) { it.name }
