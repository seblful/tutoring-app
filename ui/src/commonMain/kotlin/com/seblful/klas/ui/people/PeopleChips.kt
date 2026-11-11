package com.seblful.klas.ui.people

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ActiveFilter
import com.seblful.klas.ui.common.ActiveFilters
import com.seblful.klas.ui.common.FilterMenu
import com.seblful.klas.ui.common.MenuHeading
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.groups.GroupSort
import com.seblful.klas.ui.groups.SubjectChoice
import com.seblful.klas.ui.students.StudentSort

/*
 * People's narrowing and order, behind the bar's filter icon, so the list starts under the tabs; what is in
 * force shows over the list as chips that take it off (R5).
 */

/** The students' menu: «Owes · 3» narrows to debtors, «Archived» lists the retired too, then the order. */
@Composable
fun StudentFilterAction(
    owing: Int,
    owesOnly: Boolean,
    onOwes: () -> Unit,
    archived: Boolean,
    onArchived: () -> Unit,
    sort: StudentSort,
    onSort: (StudentSort) -> Unit,
) {
    FilterMenu(stringResource(Strings.people_filter)) { close ->
        MenuHeading(stringResource(Strings.people_show))
        if (owing > 0 || owesOnly) {
            TickedMenuItem(stringResource(Strings.people_owes, owing), owesOnly, onClick = { close(); onOwes() })
        }
        TickedMenuItem(stringResource(Strings.people_archived), archived, onClick = { close(); onArchived() })
        Order(StudentSort.entries, sort, { close(); onSort(it) }) { stringResource(studentSortLabel(it)) }
    }
}

/** The students' narrowings in force. */
@Composable
fun StudentFilters(
    owing: Int,
    owesOnly: Boolean,
    onOwes: () -> Unit,
    archived: Boolean,
    onArchived: () -> Unit,
) {
    PeopleFilters(
        listOfNotNull(
            ActiveFilter(stringResource(Strings.people_owes, owing), onOwes).takeIf { owesOnly },
            ActiveFilter(stringResource(Strings.people_archived), onArchived).takeIf { archived },
        ),
    )
}

/** The groups' menu: a subject, once they are taught two, then «Archived» and the order, as the students'. */
@Composable
fun GroupFilterAction(
    subjects: List<SubjectChoice>,
    subject: Long?,
    onSubject: (Long?) -> Unit,
    archived: Boolean,
    onArchived: () -> Unit,
    sort: GroupSort,
    onSort: (GroupSort) -> Unit,
) {
    FilterMenu(stringResource(Strings.people_filter)) { close ->
        if (subjects.size >= 2) {
            MenuHeading(stringResource(Strings.people_subject))
            TickedMenuItem(
                stringResource(Strings.people_all_subjects),
                selected = subject == null,
                onClick = { close(); onSubject(null) },
            )
            subjects.forEach { choice ->
                TickedMenuItem(choice.name, choice.id == subject, onClick = { close(); onSubject(choice.id) })
            }
            KlasDivider()
        }
        MenuHeading(stringResource(Strings.people_show))
        TickedMenuItem(stringResource(Strings.people_archived), archived, onClick = { close(); onArchived() })
        Order(GroupSort.entries, sort, { close(); onSort(it) }) { stringResource(groupSortLabel(it)) }
    }
}

/** The groups' narrowings in force. */
@Composable
fun GroupFilters(
    subjects: List<SubjectChoice>,
    subject: Long?,
    onSubject: (Long?) -> Unit,
    archived: Boolean,
    onArchived: () -> Unit,
) {
    PeopleFilters(
        listOfNotNull(
            subjects.firstOrNull { it.id == subject }?.let { ActiveFilter(it.name) { onSubject(null) } },
            ActiveFilter(stringResource(Strings.people_archived), onArchived).takeIf { archived },
        ),
    )
}

@Composable
private fun PeopleFilters(active: List<ActiveFilter>) {
    ActiveFilters(active, Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.tight))
}

@Composable
private fun <T> ColumnScope.Order(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
) {
    KlasDivider()
    MenuHeading(stringResource(Strings.people_order))
    options.forEach { option -> TickedMenuItem(label(option), option == selected, onClick = { onSelect(option) }) }
}

private fun studentSortLabel(sort: StudentSort): StringResource = when (sort) {
    StudentSort.NAME -> Strings.students_sort_name
    StudentSort.DEBT -> Strings.students_sort_debt
}

private fun groupSortLabel(sort: GroupSort): StringResource = when (sort) {
    GroupSort.NAME -> Strings.students_sort_name
    GroupSort.NEXT_LESSON -> Strings.groups_sort_next
}
