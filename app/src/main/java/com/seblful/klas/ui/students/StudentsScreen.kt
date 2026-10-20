package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.common.ArchivedBadge
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabListPadding
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.NoSearchResults
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.theme.KlasIcons

/**
 * The students list — a **body**, without a scaffold of its own.
 *
 * @param searching whether the empty list is empty because nothing matched, or because there is
 * nothing. The two need different words and only one of them has an action.
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
            ListSkeleton(modifier)
            return
        }
        is Loadable.Ready -> state.value
    }
    if (rows.isEmpty()) {
        // "Nothing matched «зщ»" is a typo to fix; "no students yet" is an invitation. Offering to
        // create a student in the first case would be answering a question nobody asked.
        if (searching) {
            NoSearchResults(modifier)
        } else {
            EmptyState(
                title = stringResource(R.string.students_empty),
                detail = stringResource(R.string.students_empty_detail),
                icon = KlasIcons.Outlined.Person,
                actionLabel = stringResource(R.string.students_add),
                onAction = onAddStudent,
                modifier = modifier,
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        // Past the add button, so it never sits on the last row.
        contentPadding = FabListPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        items(rows, key = { it.student.id }) { row ->
            ContentCard(
                onClick = { onStudentClick(row.student.id) },
                // Archiving somebody, or re-sorting by debt, moves rows. Without this they teleport,
                // which reads as a glitch rather than as a change.
                modifier = animateInPlace(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(row.student.name, row.student.id)
                    Spacer(Modifier.width(Spacing.leading))
                    Column(Modifier.weight(1f)) {
                        Text(row.student.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = balanceDescription(row.state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = balanceColor(row.state),
                        )
                    }
                    // A badge, like every other state the app shows. This was an italic grey word — the one
                    // place a state was said in a *font style* rather than in the pill the lesson statuses
                    // use, so «в архиве» read as an aside about the row rather than as a fact about the
                    // student, and the emphasis carried nothing to a screen reader.
                    if (row.student.archived) {
                        Spacer(Modifier.width(Spacing.inline))
                        ArchivedBadge()
                    }
                }
            }
        }
    }
}
