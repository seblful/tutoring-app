package com.seblful.klas.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.format.NameOrder
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.PeopleSearch
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.ready
import com.seblful.klas.ui.people.PeopleSide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

data class StudentRow(val student: Student, val balance: Money)

/**
 * How the students list is ordered.
 *
 * Two orders because the tutor asks two questions of this screen, and only one of them was answerable
 * before: "where is Аня" (by name) and "who owes me money" (by debt). The second used to be the
 * reports screen's alone, which meant reading a name there and then hunting for it here.
 */
enum class StudentSort { NAME, DEBT }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
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
     *
     * [Loadable] because an empty list is an answer and the absence of one is not: starting at
     * `emptyList()` meant a tutor with forty students was invited to add their first for the frame or
     * two before the query returned.
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
                val balances = Billing.balancesFor(studentList, allClasses, allPayments)
                studentList.zip(balances) { student, balance -> StudentRow(student, balance.balance) }
                    .filter { PeopleSearch.matches(it.student.name, query) }
                    .sortedWith(sort.comparator)
            }
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /**
     * Debt first, then by name.
     *
     * The tie-break is what makes the debt order usable: everyone settled has a balance of zero, so
     * without it two thirds of the list would sit in whatever order the query happened to return —
     * and it would shuffle as balances changed.
     */
    private val StudentSort.comparator: Comparator<StudentRow>
        get() = when (this) {
            StudentSort.NAME -> byName
            StudentSort.DEBT -> compareByDescending<StudentRow> { it.balance.minor }.then(byName)
        }

    /** The app's one name order, over a row — not a second spelling of it. See [NameOrder.by]. */
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
