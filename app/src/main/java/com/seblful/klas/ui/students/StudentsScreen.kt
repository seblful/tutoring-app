package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.balanceColor
import com.seblful.klas.ui.common.balanceDescription
import com.seblful.klas.ui.common.constrainedContent

/**
 * The students list — a **body**, without a scaffold of its own.
 *
 * It shares one with the groups list (see `ui/people/PeopleScreen.kt`): the two are views of the same
 * set of people, so they sit under one app bar and one segmented control rather than in two tabs.
 *
 * Takes its rows already filtered and sorted. The screen used to hold no state at all and now has a
 * search behind it, which is exactly the point at which "the composable will just filter it" starts
 * running string comparisons on the frame it is drawing — so that work stays in the view model, on
 * the computation dispatcher, beside the balance arithmetic it travels with.
 *
 * Three states, not two. «Пока нет учеников» is an invitation and it must only ever be extended to a
 * tutor who genuinely has none — so a list that has not been read yet draws a skeleton instead. The
 * distinction arrives as a type ([Loadable]) rather than being inferred from emptiness, which is what it
 * used to be: the initial `emptyList()` and a genuinely empty database were the same value.
 *
 * @param searching whether the empty list is empty because nothing matched, or because there is
 *   nothing. The two need different words and only one of them has an action.
 */
@Composable
fun StudentsList(
    state: Loadable<List<StudentRow>>,
    searching: Boolean,
    onStudentClick: (Long) -> Unit,
    onAddStudent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = when (state) {
        Loadable.Loading -> {
            ListSkeleton(modifier.constrainedContent())
            return
        }
        is Loadable.Ready -> state.value
    }
    if (rows.isEmpty()) {
        // "Nothing matched «зщ»" is a typo to fix; "no students yet" is an invitation. Offering to
        // create a student in the first case would be answering a question nobody asked.
        if (searching) {
            EmptyState(
                title = stringResource(R.string.search_no_results),
                detail = stringResource(R.string.search_no_results_detail),
                icon = Icons.Filled.SearchOff,
                modifier = modifier,
            )
        } else {
            EmptyState(
                title = stringResource(R.string.students_empty),
                detail = stringResource(R.string.students_empty_detail),
                icon = Icons.Filled.PersonOutline,
                actionLabel = stringResource(R.string.students_add),
                onAction = onAddStudent,
                modifier = modifier,
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize().constrainedContent(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rows, key = { it.student.id }) { row ->
            ContentCard(
                onClick = { onStudentClick(row.student.id) },
                // Archiving somebody, or re-sorting by debt, moves rows. Without this they teleport,
                // which reads as a glitch rather than as a change.
                modifier = Modifier.animateItem(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(row.student.name, row.student.id)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.student.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = balanceDescription(row.balance),
                            style = MaterialTheme.typography.bodyMedium,
                            color = balanceColor(row.balance),
                        )
                    }
                    if (row.student.archived) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.student_archived_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }
}
