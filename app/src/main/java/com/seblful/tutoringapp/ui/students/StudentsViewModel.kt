package com.seblful.tutoringapp.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.data.repository.PaymentRepository
import com.seblful.tutoringapp.data.repository.StudentRepository
import com.seblful.tutoringapp.domain.Billing
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Student
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val showArchived = MutableStateFlow(false)

    val rows: StateFlow<List<StudentRow>> =
        showArchived.flatMapLatest { archived ->
            val source = if (archived) students.observeAll() else students.observeActive()
            combine(source, classes.observeAll(), payments.observeAll()) { studentList, allClasses, allPayments ->
                val classesByStudent = allClasses.groupBy { it.studentId }
                val paymentsByStudent = allPayments.groupBy { it.studentId }
                studentList.map { student ->
                    val balance = Billing.balanceOf(
                        student.id,
                        classesByStudent[student.id].orEmpty(),
                        paymentsByStudent[student.id].orEmpty(),
                    ).balance
                    StudentRow(student, balance)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleArchived() {
        showArchived.value = !showArchived.value
    }

    fun addStudent(name: String, note: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { students.upsert(Student(name = trimmed, note = note.trim())) }
    }
}
