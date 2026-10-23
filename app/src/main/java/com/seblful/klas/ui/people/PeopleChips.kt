package com.seblful.klas.ui.people

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.designsystem.component.FilterBar
import com.seblful.klas.ui.designsystem.component.FilterOption
import com.seblful.klas.ui.designsystem.component.MenuChip
import com.seblful.klas.ui.groups.GroupSort
import com.seblful.klas.ui.groups.SubjectChoice
import com.seblful.klas.ui.students.StudentSort

/**
 * The students' chips under People's tabs (R5): «Owes · 3» narrows to debtors, «Archived» lists the retired too,
 * and the order is a chip of its own — three chips that say what they are, where a sort icon and an archive
 * icon had to be guessed.
 */
@Composable
internal fun StudentChips(
    owing: Int,
    owesOnly: Boolean,
    onOwes: () -> Unit,
    archived: Boolean,
    onArchived: () -> Unit,
    sort: StudentSort,
    onSort: (StudentSort) -> Unit,
) {
    FilterBar(
        listOfNotNull(
            FilterOption(stringResource(R.string.people_owes, owing), owesOnly, onOwes).takeIf {
                owing > 0 || owesOnly
            },
            FilterOption(stringResource(R.string.people_archived), archived, onArchived),
        ),
        Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
    ) {
        SortChip(StudentSort.entries, sort, onSort) { stringResource(studentSortLabel(it)) }
    }
}

/** The groups' chips: a subject, once they are taught two, then «Archived» and the order, as the students'. */
@Composable
internal fun GroupChips(
    subjects: List<SubjectChoice>,
    subject: Long?,
    onSubject: (Long?) -> Unit,
    archived: Boolean,
    onArchived: () -> Unit,
    sort: GroupSort,
    onSort: (GroupSort) -> Unit,
) {
    FilterBar(
        listOf(FilterOption(stringResource(R.string.people_archived), archived, onArchived)),
        Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
    ) {
        if (subjects.size >= 2) SubjectChip(subjects, subject, onSubject)
        SortChip(GroupSort.entries, sort, onSort) { stringResource(groupSortLabel(it)) }
    }
}

@Composable
private fun SubjectChip(subjects: List<SubjectChoice>, subject: Long?, onSubject: (Long?) -> Unit) {
    val all = stringResource(R.string.people_all_subjects)
    MenuChip(label = subjects.firstOrNull { it.id == subject }?.name ?: all, narrowed = subject != null) { close ->
        TickedMenuItem(all, selected = subject == null, onClick = { close(); onSubject(null) })
        subjects.forEach { choice ->
            TickedMenuItem(choice.name, selected = choice.id == subject, onClick = { close(); onSubject(choice.id) })
        }
    }
}

/** The order as a chip, untinted in the default order (the first), so only a surprising order draws the eye. */
@Composable
private fun <T> SortChip(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    val chosen = stringResource(R.string.people_sort, label(selected))
    MenuChip(label = chosen, narrowed = selected != options.first()) { close ->
        options.forEach { option ->
            TickedMenuItem(label(option), selected = option == selected, onClick = { close(); onSelect(option) })
        }
    }
}

private fun studentSortLabel(sort: StudentSort): Int = when (sort) {
    StudentSort.NAME -> R.string.students_sort_name
    StudentSort.DEBT -> R.string.students_sort_debt
}

private fun groupSortLabel(sort: GroupSort): Int = when (sort) {
    GroupSort.NAME -> R.string.students_sort_name
    GroupSort.NEXT_LESSON -> R.string.groups_sort_next
}
