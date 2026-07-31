package com.seblful.klas.ui.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.PinnedHeader
import com.seblful.klas.ui.common.ScreenSearch
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.swipeBetweenSegments
import com.seblful.klas.ui.groups.GroupsList
import com.seblful.klas.ui.groups.GroupsViewModel
import com.seblful.klas.ui.students.StudentSort
import com.seblful.klas.ui.students.StudentsList
import com.seblful.klas.ui.students.StudentsViewModel

/** The two ways of looking at the same people: one at a time, or taught together. */
private enum class PeopleTab(val labelRes: Int) {
    STUDENTS(R.string.students_title),
    GROUPS(R.string.groups_title),
}

/**
 * «Люди» — students and groups under one tab.
 *
 * They were two, plus a setting to hide one of them, plus a conditional entry in «Ещё» for when it
 * was hidden, plus visibility plumbing through the destination list so the bottom bar would not claim
 * a route it was not showing. All of that existed to answer a question the sections do not actually
 * pose: **a group is students seen as a roster**, so it is a view of this screen rather than a screen
 * of its own. Merging them deletes the setting, the conditional entry point and the plumbing, and
 * takes the bottom bar down to four tabs — where the labels stop needing to shrink to fit.
 *
 * The state that differs per tab (what is being searched for, whether the archive is shown) lives in
 * the two view models, which is what lets switching tabs preserve each side's search rather than
 * clearing both. The bar reaches them through one seam — [PeopleSide], which both implement — so the
 * side on top is picked **once**, instead of once per button in dispatch branches no test can flip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onStudentClick: (Long) -> Unit,
    onAddStudent: () -> Unit,
    onGroupClick: (Long) -> Unit,
    onAddGroup: () -> Unit,
    studentsViewModel: StudentsViewModel = hiltViewModel(),
    groupsViewModel: GroupsViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(PeopleTab.STUDENTS) }

    val studentRows by studentsViewModel.rows.collectAsState()
    val studentSort by studentsViewModel.sort.collectAsState()
    val groupRows by groupsViewModel.rows.collectAsState()

    // Whichever side is on top answers the bar's buttons; the other keeps its own state untouched.
    val side: PeopleSide = when (tab) {
        PeopleTab.STUDENTS -> studentsViewModel
        PeopleTab.GROUPS -> groupsViewModel
    }
    val query by side.query.collectAsState()
    val archived by side.showArchived.collectAsState()

    KlasScreen(
        title = stringResource(R.string.nav_people),
        // One search box for two lists. Typing goes to whichever side is on top, so switching tab keeps
        // each side's own search — but **closing clears both**, because the shut box can only speak for
        // one of them: searching «ан» on Students, switching to Groups and closing used to leave the
        // students list filtered with nothing on screen to say why, or to undo it.
        search = ScreenSearch(
            query = query,
            placeholder = stringResource(
                if (tab == PeopleTab.STUDENTS) R.string.students_search_hint
                else R.string.groups_search_hint
            ),
            onQueryChange = side::setQuery,
            onClose = { studentsViewModel.setQuery(""); groupsViewModel.setQuery("") },
        ),
        actions = {
            // Sorting by debt is a students question; a group carries no balance.
            if (tab == PeopleTab.STUDENTS) {
                SortMenu(selected = studentSort, onSelect = studentsViewModel::setSort)
            }
            IconButton(onClick = side::toggleArchived) {
                Icon(
                    Icons.Filled.Inventory2,
                    stringResource(R.string.students_show_archived),
                    tint = if (archived) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (tab == PeopleTab.STUDENTS) onAddStudent else onAddGroup,
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { modifier ->
        // The segments are the control; the drag is the shortcut — see [swipeBetweenSegments]. On the whole
        // body rather than on the lists alone, so the gesture is available over the band as well: the two
        // sides of «Люди» are one screen showing the same people two ways, and every part of it should turn
        // the page. Neither list owns a horizontal gesture, so nothing here competes for the drag.
        Column(modifier.swipeBetweenSegments(PeopleTab.entries, tab) { tab = it }) {
            // **Fixed, and drawn as fixed.** The control sits above the lists rather than inside them,
            // so it never scrolls — but it was painted the page's own colour, which is what made rows
            // appear to slide out from behind nothing as either list moved under it. [PinnedHeader] is
            // the same band the detail cards' tab rows use, and for the same reason.
            PinnedHeader {
                SegmentedChoice(
                    options = PeopleTab.entries,
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(
                        horizontal = Spacing.gutter,
                        vertical = Spacing.inline,
                    ),
                    label = { entry -> stringResource(entry.labelRes) },
                )
            }
            Box(Modifier.fillMaxSize()) {
                // `query` is the visible side's, so either list's "empty because nothing matched"
                // reads the same search its rows were filtered by.
                when (tab) {
                    PeopleTab.STUDENTS -> StudentsList(
                        state = studentRows,
                        searching = query.isNotBlank(),
                        onStudentClick = onStudentClick,
                        onAddStudent = onAddStudent,
                    )

                    PeopleTab.GROUPS -> GroupsList(
                        state = groupRows,
                        searching = query.isNotBlank(),
                        onGroupClick = onGroupClick,
                        onAddGroup = onAddGroup,
                    )
                }
            }
        }
    }
}

/** Name order or debt order — the two questions the students list is asked. */
@Composable
private fun SortMenu(selected: StudentSort, onSelect: (StudentSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                stringResource(R.string.students_sort),
                // Tinted while the list is in a non-default order, so a surprising order has a
                // visible cause in the bar above it.
                tint = if (selected != StudentSort.NAME) MaterialTheme.colorScheme.primary
                else LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StudentSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabel(option))) },
                    onClick = { onSelect(option); expanded = false },
                    trailingIcon = {
                        if (option == selected) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

private fun sortLabel(sort: StudentSort): Int = when (sort) {
    StudentSort.NAME -> R.string.students_sort_name
    StudentSort.DEBT -> R.string.students_sort_debt
}
