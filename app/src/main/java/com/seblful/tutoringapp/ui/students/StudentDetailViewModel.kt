package com.seblful.tutoringapp.ui.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.data.repository.PaymentRepository
import com.seblful.tutoringapp.data.repository.PriceRepository
import com.seblful.tutoringapp.data.repository.StudentRepository
import com.seblful.tutoringapp.data.repository.SubjectRepository
import com.seblful.tutoringapp.domain.Billing
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Payment
import com.seblful.tutoringapp.domain.model.Student
import com.seblful.tutoringapp.domain.model.Subject
import com.seblful.tutoringapp.domain.model.TutoringClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** A subject the student takes, with its current per-class price (null if unset). */
data class SubjectPrice(val subject: Subject, val price: Money?)

/** A class plus its subject's display name. */
data class ClassRow(val klass: TutoringClass, val subjectName: String)

data class StudentDetailState(
    val student: Student? = null,
    val balance: Money = Money.ZERO,
    val subjects: List<SubjectPrice> = emptyList(),
    val allSubjects: List<Subject> = emptyList(),
    val classes: List<ClassRow> = emptyList(),
    val payments: List<Payment> = emptyList(),
)

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val prices: PriceRepository,
    private val classes: ClassRepository,
    private val payments: PaymentRepository,
) : ViewModel() {

    private val studentId: Long = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<StudentDetailState> = combine(
        students.observeById(studentId),
        subjects.observeAll(),
        students.observeSubjectIds(studentId),
        prices.observeForStudent(studentId),
        classes.observeForStudent(studentId),
    ) { student, allSubjects, subjectIds, priceList, classList ->
        val priceBySubject = priceList.associateBy { it.subjectId }
        val subjectsById = allSubjects.associateBy { it.id }
        Partial(
            student = student,
            allSubjects = allSubjects,
            subjects = subjectIds.mapNotNull { id ->
                subjectsById[id]?.let { SubjectPrice(it, priceBySubject[id]?.amount) }
            }.sortedBy { it.subject.name.lowercase() },
            classes = classList.map { ClassRow(it, subjectsById[it.subjectId]?.name ?: "") },
        )
    }.combine(payments.observeForStudent(studentId)) { partial, paymentList ->
        StudentDetailState(
            student = partial.student,
            balance = Billing.balanceOf(studentId, partial.classes.map { it.klass }, paymentList).balance,
            subjects = partial.subjects,
            allSubjects = partial.allSubjects,
            classes = partial.classes,
            payments = paymentList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentDetailState())

    private data class Partial(
        val student: Student?,
        val allSubjects: List<Subject>,
        val subjects: List<SubjectPrice>,
        val classes: List<ClassRow>,
    )

    fun editStudent(name: String, note: String) {
        val current = state.value.student ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { students.upsert(current.copy(name = trimmed, note = note.trim())) }
    }

    fun setArchived(archived: Boolean) {
        val current = state.value.student ?: return
        viewModelScope.launch { students.setArchived(current, archived) }
    }

    fun setSubjects(subjectIds: Set<Long>) {
        viewModelScope.launch { students.setSubjects(studentId, subjectIds) }
    }

    fun setPrice(subjectId: Long, amount: Money) {
        viewModelScope.launch { prices.setPrice(studentId, subjectId, amount) }
    }

    fun addPayment(amount: Money) {
        if (amount.isZero) return
        viewModelScope.launch {
            payments.add(Payment(studentId = studentId, date = Instant.now(), amount = amount))
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch { payments.delete(payment) }
    }
}
