package com.seblful.klas.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.NearestLessons
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.people.NameOrder
import com.seblful.klas.domain.people.PeopleSearch
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.minutes
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.people.LessonLine
import com.seblful.klas.ui.people.PeopleSide
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.atTime

/** A group as the list shows it: the group, who is in it, what it is taught, and when it meets. */
data class GroupRow(
    val group: Group,
    val memberNames: List<String>,
    val subjectName: String? = null,
    val lessons: LessonLine = LessonLine.Never,
)

/** A subject the listed groups are taught — one of the groups filter's chips. */
data class SubjectChoice(val id: Long, val name: String)

/** How the groups list is ordered: by name, or by which meets soonest — a group with none booked last. */
enum class GroupSort { NAME, NEXT_LESSON }

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
    classes: ClassRepository,
    private val clock: WallClock,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel(), PeopleSide {

    private val _showArchived = MutableStateFlow(false)

    /** Whether archived groups are listed too. Changed through [toggleArchived], not written to. */
    override val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _query = MutableStateFlow("")
    override val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(GroupSort.NAME)
    val sort: StateFlow<GroupSort> = _sort.asStateFlow()

    private val _subjectFilter = MutableStateFlow<Long?>(null)

    /** The subject the list is narrowed to, or null for every group. */
    val subjectFilter: StateFlow<Long?> = _subjectFilter.asStateFlow()

    /**
     * Each group's nearest lessons, re-read every minute so a card left open moves on from a lesson once it
     * ends. **A test that collects [rows] must advance time by a stated amount, never `advanceUntilIdle()`.**
     */
    private val spans = clock.minutes().flatMapLatest { now -> classes.observeGroupSpans(now).map { it to now } }

    private val listed: Flow<List<GroupRow>> =
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
                        subjectName = group.subjectId?.let { subjectsById[it] },
                    )
                }
            }.combine(spans) { rows, (nearest, now) ->
                rows.map { row ->
                    row.copy(lessons = LessonLine.of(nearest[row.group.id] ?: NearestLessons.NONE, now, clock.zone))
                }
            }.combine(_sort) { rows, sort -> rows.sortedFor(sort) }
        }

    /** [Loadable], so "no groups yet" is never said about groups that have not been read — see `Loadable`. */
    val rows: StateFlow<Loadable<List<GroupRow>>> =
        combine(listed, _subjectFilter) { rows, subject ->
            rows.filter {
                subject == null ||
                    it.group.subjectId == subject
            }
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /** The subjects the listed groups are taught, by name — the filter offers them only when there are two. */
    val subjects: StateFlow<List<SubjectChoice>> = listed.map { rows ->
        rows.mapNotNull { row -> row.group.subjectId?.let { SubjectChoice(it, row.subjectName.orEmpty()) } }
            .distinct()
            .sortedBy { it.name.lowercase() }
    }.hold(this, emptyList())

    override fun toggleArchived() {
        _showArchived.value = !_showArchived.value
    }

    override fun setQuery(query: String) {
        _query.value = query
    }

    fun setSort(sort: GroupSort) {
        _sort.value = sort
    }

    fun setSubjectFilter(subjectId: Long?) {
        _subjectFilter.value = subjectId
    }
}

/**
 * [this] in [sort]'s order, the archive last either way. Stable, so by name keeps the repository's name order
 * and groups meeting at one moment keep theirs.
 */
internal fun List<GroupRow>.sortedFor(sort: GroupSort): List<GroupRow> = sortedWith(
    when (sort) {
        GroupSort.NAME -> compareBy { it.group.archived }
        GroupSort.NEXT_LESSON -> compareBy<GroupRow> { it.group.archived }
            .thenBy(nullsLast()) { (it.lessons as? LessonLine.Next)?.let { next -> next.date.atTime(next.time) } }
    },
)
