package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.common.ArchivedBadge
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabListPadding
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.NoSearchResults
import com.seblful.klas.ui.common.ScrollsToTopOnReselect
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.people.PeopleCard
import com.seblful.klas.ui.people.lessonLineText
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
    bottomInset: PaddingValues = PaddingValues(),
    openId: Long? = null,
    chips: @Composable () -> Unit = {},
) {
    // Above the list rather than in it, so a filter that leaves nothing can still be taken off.
    Column(modifier) {
        chips()
        StudentRows(state, searching, onStudentClick, onAddStudent, Modifier.weight(1f), bottomInset, openId)
    }
}

@Composable
private fun StudentRows(
    state: Loadable<List<StudentRow>>,
    searching: Boolean,
    onStudentClick: (Long) -> Unit,
    onAddStudent: () -> Unit,
    modifier: Modifier,
    bottomInset: PaddingValues,
    openId: Long?,
) {
    val rows = when (state) {
        Loadable.Loading -> {
            ListSkeleton(modifier.padding(bottomInset))
            return
        }
        is Loadable.Ready -> state.value
    }
    if (rows.isEmpty()) {
        // "Nothing matched «зщ»" is a typo to fix; "no students yet" is an invitation. Offering to
        // create a student in the first case would be answering a question nobody asked.
        if (searching) {
            NoSearchResults(modifier.padding(bottomInset))
        } else {
            EmptyState(
                title = stringResource(R.string.students_empty),
                detail = stringResource(R.string.students_empty_detail),
                icon = KlasIcons.Outlined.Person,
                actionLabel = stringResource(R.string.students_add),
                onAction = onAddStudent,
                modifier = modifier.padding(bottomInset),
            )
        }
        return
    }
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(listState)
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        // Past the add button, so it never sits on the last row.
        contentPadding = FabListPadding.plusInset(bottomInset),
    ) {
        itemsIndexed(rows, key = { _, row -> row.student.id }, contentType = { _, _ -> "student" }) { index, row ->
            // Archiving somebody, or re-sorting by debt, moves rows. Without this they teleport, which reads as a
            // glitch rather than as a change.
            StudentCard(
                row,
                onClick = { onStudentClick(row.student.id) },
                modifier = animateInPlace(),
                selected = row.student.id == openId,
                position = SlicePosition.of(index, rows.size),
            )
        }
    }
}

/** One student on the list: what they take and when they are next taught, with any balance at the name's end. */
@Composable
internal fun StudentCard(
    row: StudentRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    position: SlicePosition = SlicePosition.ONLY,
) {
    PeopleCard(
        title = row.student.name,
        supporting = bulletJoin(lessonLineText(row.lessons), row.subjects.joinToString(", ")),
        onClick = onClick,
        leading = { Avatar(row.student.name, row.student.id) },
        modifier = modifier,
        selected = selected,
        position = position,
    ) {
        // Only a balance that is something — a debt or a credit. «Нет задолженности» on most rows would be the same
        // news repeated, drowning out the rows that differ.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
            if (row.student.archived) ArchivedBadge(Modifier.alignByBaseline())
            if (row.state != Balance.Settled) {
                Text(
                    text = balanceDescription(row.state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = balanceColor(row.state),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}
