package com.seblful.klas.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentRow(val student: Student, val balance: Money)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StudentsViewModel @Inject constructor(
    private val students: StudentRepository,
    classes: ClassRepository,
    payments: PaymentRepository,
) : ViewModel() {

    private val _showArchived = MutableStateFlow(false)

    /** Whether archived students are listed too. Changed through [toggleArchived], not written to. */
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    val rows: StateFlow<List<StudentRow>> =
        _showArchived.flatMapLatest { archived ->
            val source = if (archived) students.observeAll() else students.observeActive()
            combine(source, classes.observeAll(), payments.observeAll()) { studentList, allClasses, allPayments ->
                val balances = Billing.balancesFor(studentList, allClasses, allPayments)
                studentList.zip(balances) { student, balance -> StudentRow(student, balance.balance) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleArchived() {
        _showArchived.value = !_showArchived.value
    }
}
