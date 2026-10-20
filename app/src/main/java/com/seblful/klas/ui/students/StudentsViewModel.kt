package com.seblful.klas.ui.students

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
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

data class StudentRow(val student: Student, val balance: Money) {
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
 * Two orders because the tutor asks two questions of this screen, and only one of them was answerable
 * before: "where is Аня" (by name) and "who owes me money" (by debt). The second used to be the
 * reports screen's alone, which meant reading a name there and then hunting for it here.
 */
enum class StudentSort { NAME, DEBT }

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StudentsViewModel @Inject constructor(
    private val students: StudentRepository,
    classes: ClassRepository,
    payments: PaymentRepository,
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

    /**
     * The rows to draw. Filtering and sorting happen **here**, not in the composable, so they run on
     * [computation] with the balance arithmetic they depend on rather than on the frame the list is
     * being drawn in.
     */
    val rows: StateFlow<Loadable<List<StudentRow>>> =
        _showArchived.flatMapLatest { archived ->
            val source = if (archived) students.observeAll() else students.observeActive()
            combine(
                source,
                classes.observeAll(),
                payments.observeAll(),
                _query,
                _sort,
            ) { studentList, allClasses, allPayments, query, sort ->
                // Narrowed before the balances, so a search sums the money of the students it shows only.
                val shown = PeopleSearch.filter(studentList, query) { it.name }
                val balances = Billing.balancesFor(shown, allClasses, allPayments)
                shown.zip(balances) { student, balance -> StudentRow(student, balance.balance) }
                    // The archive after everyone taught now, as the groups list keeps it: sorting by name
                    // alone threaded the retired through the list, and «Показать архив» turned the page
                    // into one where the current students had to be picked out.
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
