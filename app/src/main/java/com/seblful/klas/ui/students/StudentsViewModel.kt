package com.seblful.klas.ui.students

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.billing.BalanceRepository
import com.seblful.klas.data.billing.balanceOf
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.NearestLessons
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.NameOrder
import com.seblful.klas.domain.people.PeopleSearch
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.minutes
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.derive
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
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/**
 * @param subjects what the student takes, by name in the list's order.
 * @param lessons their next lesson, or their last — see [LessonLine].
 */
data class StudentRow(
    val student: Student,
    val balance: Money,
    val subjects: List<String> = emptyList(),
    val lessons: LessonLine = LessonLine.Never,
) {
    /**
     * Where this student stands, as a case rather than a sign — what the row prints and tints.
     *
     * The raw [balance] stays beside it because [StudentSort.DEBT] orders by the signed amount, which is
     * a question about magnitude and not about which of the three cases it is.
     */
    val state: Balance get() = Balance.of(balance)
}

/**
 * How the students list is ordered.
 *
 * Two orders because the tutor asks two questions of this screen: "where is Аня" (by name) and "who owes
 * me money" (by debt). Answering the second here spares reading a name on the reports screen and then
 * hunting for it.
 */
enum class StudentSort { NAME, DEBT }

/** The «Owes» chip's and the filter's test, the row badge's own [Balance] case. */
private val Money.owes: Boolean get() = Balance.of(this) is Balance.Owed

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StudentsViewModel @Inject constructor(
    private val students: StudentRepository,
    balances: BalanceRepository,
    classes: ClassRepository,
    enrollments: EnrollmentRepository,
    subjects: SubjectRepository,
    private val clock: WallClock,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel(), PeopleSide {

    private val _showArchived = MutableStateFlow(false)

    /** Whether archived students are listed too. Changed through [toggleArchived], not written to. */
    override val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _query = MutableStateFlow("")

    /** What the tutor is searching for; empty means the list is unfiltered. See [setQuery]. */
    override val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(StudentSort.NAME)
    val sort: StateFlow<StudentSort> = _sort.asStateFlow()

    private val _owesOnly = MutableStateFlow(false)

    /** Whether only those who owe are listed — the «Owes · N» chip. */
    val owesOnly: StateFlow<Boolean> = _owesOnly.asStateFlow()

    /** How many active students owe — the N on that chip. */
    val owing: StateFlow<Int> =
        combine(students.observeActive(), balances.observe()) { active, sums ->
            active.count { sums.balanceOf(it.id).balance.owes }
        }
            .derive(this, computation, 0)

    fun toggleOwes() = _owesOnly.update { !it }

    private class Facts(val subjects: Map<Long, List<String>>, val lessons: Map<Long, NearestLessons>, val now: Instant)

    /**
     * Each student's subject names and nearest lessons. Re-read every minute, so a card left open moves on
     * from a lesson once it ends. **A test that collects [rows] must advance time by a stated amount, never
     * `advanceUntilIdle()`** — the minutes never run out.
     */
    private val facts: Flow<Facts> = run {
        val names = combine(enrollments.observeSubjectIds(), subjects.observeAll()) { taken, subjectList ->
            val byId = subjectList.associate { it.id to it.name }
            taken.mapValues { (_, ids) -> NameOrder.sorted(ids.mapNotNull(byId::get)) { it } }
        }
        val lessons = clock.minutes().flatMapLatest { now -> classes.observeStudentSpans(now).map { it to now } }
        combine(names, lessons) { subjectNames, (spans, now) -> Facts(subjectNames, spans, now) }
    }

    /**
     * The rows to draw. Filtering and sorting happen **here**, not in the composable, so they run on
     * [computation] with the balance arithmetic they depend on rather than on the frame the list is
     * being drawn in.
     */
    val rows: StateFlow<Loadable<List<StudentRow>>> =
        _showArchived.flatMapLatest { archived ->
            val source = if (archived) students.observeAll() else students.observeActive()
            // SQLite sums the balances, so neither a keystroke nor a write re-reads every class ever taught.
            val asked = combine(_query, _owesOnly, ::Pair)
            combine(source, balances.observe(), facts, asked, _sort) { studentList, sums, known, (query, owes), sort ->
                PeopleSearch.filter(studentList, query) { it.name }
                    .filter { !owes || sums.balanceOf(it.id).balance.owes }
                    .map { student ->
                        StudentRow(
                            student = student,
                            balance = sums.balanceOf(student.id).balance,
                            subjects = known.subjects[student.id].orEmpty(),
                            lessons = LessonLine.of(
                                known.lessons[student.id] ?: NearestLessons.NONE, known.now, clock.zone,
                            ),
                        )
                    }
                    // The archive after everyone taught now, as the groups list keeps it: sorting by name
                    // alone would thread the retired through the list, and «Показать архив» would turn the
                    // page into one where the current students had to be picked out.
                    .sortedWith(compareBy<StudentRow> { it.student.archived }.then(sort.comparator))
            }
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    private val StudentSort.comparator: Comparator<StudentRow>
        get() = when (this) {
            StudentSort.NAME -> byName
            StudentSort.DEBT -> compareByDescending<StudentRow> { it.balance.minor }.then(byName)
        }

    private val byName: Comparator<StudentRow> = NameOrder.by { it.student.name }

    override fun toggleArchived() {
        _showArchived.value = !_showArchived.value
    }

    override fun setQuery(query: String) {
        _query.value = query
    }

    fun setSort(sort: StudentSort) {
        _sort.value = sort
    }
}
