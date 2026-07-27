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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
) : ViewModel() {

    private val _showArchived = MutableStateFlow(false)

    /** Whether archived students are listed too. Changed through [toggleArchived], not written to. */
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _query = MutableStateFlow("")

    /** What the tutor is searching for; empty means the list is unfiltered. See [setQuery]. */
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(StudentSort.NAME)
    val sort: StateFlow<StudentSort> = _sort.asStateFlow()

    /**
     * The rows to draw. Filtering and sorting happen **here**, not in the composable, so they run on
     * [computation] with the balance arithmetic they depend on rather than on the frame the list is
     * being drawn in.
     */
    val rows: StateFlow<List<StudentRow>> =
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
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun toggleArchived() {
        _showArchived.value = !_showArchived.value
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setSort(sort: StudentSort) {
        _sort.value = sort
    }
}
