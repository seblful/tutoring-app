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
import com.seblful.klas.ui.theme.KlasIcons

/** Narrows a long roster by name — forty students is a long scroll to find the four in a group. */
@Composable
internal fun MemberSearch(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.students_search_hint)) },
        leadingIcon = { Icon(KlasIcons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** How many students a roster holds before it can be searched. */
internal const val MemberSearchFrom = 8

/** The students whose name holds [query], ignoring case and the spaces typed around it; all of them for none. */
internal fun List<Student>.matching(query: String): List<Student> =
    query.trim().let { typed -> filter { it.name.contains(typed, ignoreCase = true) } }
