package com.seblful.klas.ui.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.ui.common.HoldToName
import com.seblful.klas.ui.common.KlasMenu
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.ScreenSearch
import com.seblful.klas.ui.common.SegmentedPage
import com.seblful.klas.ui.common.SettingsGear
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.designsystem.component.KlasFab
import com.seblful.klas.ui.groups.GroupSort
import com.seblful.klas.ui.groups.GroupsList
import com.seblful.klas.ui.groups.GroupsViewModel
import com.seblful.klas.ui.students.StudentSort
import com.seblful.klas.ui.students.StudentsList
import com.seblful.klas.ui.students.StudentsViewModel
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel

private enum class PeopleTab(val labelRes: Int) {
    STUDENTS(R.string.students_title),
    GROUPS(R.string.groups_title),
}

/**
 * «Люди» — students and groups under one tab.
 *
 * @param open the student or group whose card is open beside the list, marked on it; null with none beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onStudentClick: (Long) -> Unit,
    onAddStudent: () -> Unit,
    onGroupClick: (Long) -> Unit,
    onAddGroup: () -> Unit,
    onSettings: () -> Unit = {},
    open: Assignee? = null,
    studentsViewModel: StudentsViewModel = metroViewModel(),
    groupsViewModel: GroupsViewModel = metroViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(PeopleTab.STUDENTS) }

    val studentRows by studentsViewModel.rows.collectAsStateWithLifecycle()
    val studentSort by studentsViewModel.sort.collectAsStateWithLifecycle()
    val groupRows by groupsViewModel.rows.collectAsStateWithLifecycle()
    val groupSort by groupsViewModel.sort.collectAsStateWithLifecycle()
    val groupSubject by groupsViewModel.subjectFilter.collectAsStateWithLifecycle()
    val groupSubjects by groupsViewModel.subjects.collectAsStateWithLifecycle()
    val owing by studentsViewModel.owing.collectAsStateWithLifecycle()
    val owesOnly by studentsViewModel.owesOnly.collectAsStateWithLifecycle()

    // Whichever side is on top answers the bar's buttons; the other keeps its own state untouched.
    val side: PeopleSide = when (tab) {
        PeopleTab.STUDENTS -> studentsViewModel
        PeopleTab.GROUPS -> groupsViewModel
    }
    val query by side.query.collectAsStateWithLifecycle()
    val archived by side.showArchived.collectAsStateWithLifecycle()

    KlasScreen(
        title = stringResource(R.string.nav_people),
        page = tab,
        // One search box for two lists. Typing goes to whichever side is on top, so switching tab keeps
        // each side's own search — but **closing clears both**, because the shut box can only speak for
        // one of them: searching «ан» on Students, switching to Groups and closing used to leave the
        // students list filtered with nothing on screen to say why, or to undo it.
        search = ScreenSearch(
            query = query,
            placeholder = stringResource(
                if (tab == PeopleTab.STUDENTS) R.string.students_search_hint
                else R.string.groups_search_hint,
            ),
            onQueryChange = side::setQuery,
            onClose = { studentsViewModel.setQuery(""); groupsViewModel.setQuery("") },
        ),
        actions = {
            SettingsGear(onSettings)
        },
        floatingActionButton = {
            // Named for what it adds, which the side decides: a bare «Добавить» said neither.
            KlasFab(
                label = stringResource(
                    if (tab ==
                        PeopleTab.STUDENTS
                    ) R.string.fab_new_student else R.string.fab_new_group,
                ),
                onClick = if (tab == PeopleTab.STUDENTS) onAddStudent else onAddGroup,
            )
        },
    ) { modifier, bottomInset ->
        // The segments are the control; the drag is the shortcut — see [swipeBetweenSegments]. On the whole
        // body rather than on the lists alone, so the gesture is available over the band as well: the two
        // sides of «Люди» are one screen showing the same people two ways, and every part of it should turn
        // the page. Neither list owns a horizontal gesture, so nothing here competes for the drag.
        //
        // The segments sit under the bar and are painted with it ([SegmentedPage]), like the schedule's view
        // and the report's period: page-coloured at rest, so the app's segmented controls look like one
        // control wherever they are; tinted with the bar once a list is passing beneath it, so the rows
        // disappear behind one edge rather than at a line nobody drew.
        SegmentedPage(
            options = PeopleTab.entries,
            selected = tab,
            onSelect = { tab = it },
            label = { entry -> stringResource(entry.labelRes) },
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize()) {
                // `query` is the visible side's, so either list's "empty because nothing matched"
                // reads the same search its rows were filtered by.
                when (tab) {
                    PeopleTab.STUDENTS -> StudentsList(
                        state = studentRows,
                        // Debtors only that leaves nothing is "nothing matched", not "no students yet".
                        searching = query.isNotBlank() || owesOnly,
                        chips = {
                            StudentChips(
                                owing, owesOnly, studentsViewModel::toggleOwes,
                                archived, side::toggleArchived,
                                studentSort, studentsViewModel::setSort,
                            )
                        },
                        onStudentClick = onStudentClick,
                        onAddStudent = onAddStudent,
                        bottomInset = bottomInset,
                        openId = (open as? Assignee.Student)?.studentId,
                    )

                    PeopleTab.GROUPS -> GroupsList(
                        state = groupRows,
                        // A subject that leaves nothing is "nothing matched", not "no groups yet".
                        searching = query.isNotBlank() || groupSubject != null,
                        chips = {
                            GroupChips(
                                groupSubjects, groupSubject, groupsViewModel::setSubjectFilter,
                                archived, side::toggleArchived,
                                groupSort, groupsViewModel::setSort,
                            )
                        },
                        onGroupClick = onGroupClick,
                        onAddGroup = onAddGroup,
                        bottomInset = bottomInset,
                        openId = (open as? Assignee.Group)?.groupId,
                    )
                }
            }
        }
    }
}
