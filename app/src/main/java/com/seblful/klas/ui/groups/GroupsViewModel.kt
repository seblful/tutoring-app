package com.seblful.klas.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.people.NameOrder
import com.seblful.klas.domain.people.PeopleSearch
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.people.PeopleSide
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**  group as the list shows it: the group, who is in it, and what it is taught. */
data class GroupRow(
    val group: Group,
    val memberNames: List<String>,
    val memberIds: List<Long> = emptyList(),
    val subjectName: String? = null,
)

/**
 * The groups list. Read-only on purpose — editing, archiving and deleting a group all live in the
 * group form, so there is one place where a roster changes (mirroring students: a read-only list, a
 * detail screen that mutates).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class GroupsViewModel @Inject constructor(
    groups: GroupRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel(), PeopleSide {

    private val _showArchived = MutableStateFlow(false)

    /** Whether archived groups are listed too. Changed through [toggleArchived], not written to. */
    override val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _query = MutableStateFlow("")
    override val query: StateFlow<String> = _query.asStateFlow()

    /** [Loadable], so "no groups yet" is never said about groups that have not been read — see `Loadable`. */
    val rows: StateFlow<Loadable<List<GroupRow>>> =
        _showArchived.flatMapLatest { archived ->
            val source = if (archived) groups.observeAll() else groups.observeActive()
            combine(
                source,
                groups.observeRosters(),
                // All students, not just active ones: an archived member still explains a roster.
                students.observeAll(),
                subjects.observeAll(),
                _query,
            ) { groupList, rosters, studentList, subjectList, query ->
                val studentsById = studentList.associateBy { it.id }
                val subjectsById = subjectList.associate { it.id to it.name }
                // Searched by the group's own name only. A roster search would find «Аня» inside three
                // groups and leave the tutor to work out why — the students tab beside this one is where a
                // person is looked up.
                PeopleSearch.filter(groupList, query) { it.name }.map { group ->
                    // Through NameOrder, like every other listing of people: this used to be
                    // `sortedBy { it.name }`, which does not case-fold at all, so a roster sorted
                    // every Cyrillic capital before every lower-case name.
                    val members = NameOrder.sorted(
                        rosters[group.id].orEmpty().mapNotNull { studentsById[it] },
                    ) { it.name }
                    GroupRow(
                        group = group,
                        memberNames = members.map { it.name },
                        memberIds = members.map { it.id },
                        subjectName = group.subjectId?.let { subjectsById[it] },
                    )
                }
            }
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    override fun toggleArchived() {
        _showArchived.value = !_showArchived.value
    }

    override fun setQuery(query: String) {
        _query.value = query
    }
}
