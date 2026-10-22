package com.seblful.klas.ui.homework

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.queries.homework.HomeworkLists
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.AddRowInSection
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.OverviewSection
import com.seblful.klas.ui.common.SectionNote
import com.seblful.klas.ui.common.sectionSurface

/** How many pieces of current homework a card's section shows before «See all». */
const val HOMEWORK_SHOWN = 2

/**
 * A card's homework section — a student's or a group's: the first few current ones, swiped done as in the
 * tab, and the way to set more. The done ones are only on the full list ([homeworkItems]).
 *
 * @param owner whose card this is — see [homeworkItems].
 */
@Composable
fun HomeworkSection(
    owner: Assignee?,
    lists: HomeworkLists,
    messages: Messages,
    onAdd: () -> Unit,
    onOpen: (HomeworkRow) -> Unit,
    onDone: (Homework, Boolean) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = lists.current.take(HOMEWORK_SHOWN)
    OverviewSection(
        title = stringResource(R.string.student_homework_title),
        count = lists.current.size,
        // The done ones are only on the full list, so it is worth opening while there are any.
        onSeeAll = onSeeAll.takeIf { lists.current.size > shown.size || lists.archive.isNotEmpty() },
        modifier = modifier,
    ) {
        if (lists.current.isEmpty() && lists.archive.isEmpty()) {
            SectionNote(stringResource(R.string.student_homework_empty))
        }
        shown.forEach { row ->
            // Keyed, so a row swiped done hands its place to the next rather than its swipe state.
            key(row.homework.id) {
                SwipeableHomework(
                    row = row,
                    onSwipe = { onDone(row.homework, true) },
                    onClick = { onOpen(row) },
                    messages = messages,
                    nested = true,
                    owner = owner,
                    container = MaterialTheme.colorScheme.sectionSurface,
                )
            }
        }
        AddRow(stringResource(R.string.homework_add), onAdd, AddRowInSection)
    }
}
