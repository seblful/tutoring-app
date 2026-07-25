package com.seblful.repetit.ui.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.EnrollmentRepository
import com.seblful.repetit.data.repository.PaymentRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.Billing
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Payment
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.TutoringClass
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.repetit.ui.common.UiEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * A subject the student takes, with its per-class price. The price is always set: a student's
 * subjects are exactly the subjects they have a price for — the same set that's bookable — so a
 * subject can never appear here without a price (see [StudentDetailViewModel]).
 */
data class SubjectPrice(val subject: Subject, val price: Money)

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
    private val classes: ClassRepository,
    private val payments: PaymentRepository,
    private val enrollment: EnrollmentRepository,
) : ViewModel() {

    private val studentId: Long = checkNotNull(savedStateHandle["id"])

    /** One-shot UI signals, e.g. removing a subject blocked because the student has classes for it. */
    enum class Event { REMOVE_BLOCKED_HAS_CLASSES }

    val events = UiEvents<Event>()

    val state: StateFlow<StudentDetailState> = combine(
        students.observeById(studentId),
        subjects.observeAll(),
        enrollment.observePricesFor(studentId),
        classes.observeForStudent(studentId),
    ) { student, allSubjects, priceList, classList ->
        val subjectsById = allSubjects.associateBy { it.id }
        Partial(
            student = student,
            allSubjects = allSubjects,
            // A student's subjects ARE their priced subjects — one source of truth, so the list here
            // is exactly what can be booked. No "price not set" rows, no bookable-but-hidden subjects.
            subjects = priceList.mapNotNull { p ->
                subjectsById[p.subjectId]?.let { SubjectPrice(it, p.amount) }
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

    fun editStudent(name: String, phone: String, email: String, note: String, mode: ClassMode) {
        val current = state.value.student ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            students.upsert(
                current.copy(
                    name = trimmed,
                    phone = phone.trim(),
                    email = email.trim(),
                    note = note.trim(),
                    defaultMode = mode,
                )
            )
        }
    }

    fun setArchived(archived: Boolean) {
        val current = state.value.student ?: return
        viewModelScope.launch { students.setArchived(current, archived) }
    }

    /** Adds a subject to the student with a price — [EnrollmentRepository] keeps link and price in step. */
    fun addSubject(subjectId: Long, amount: Money) {
        viewModelScope.launch { enrollment.enroll(studentId, subjectId, amount) }
    }

    /**
     * Removes a subject from the student. [EnrollmentRepository] refuses (and we signal
     * [Event.REMOVE_BLOCKED_HAS_CLASSES]) when the student has any class for it, since those classes
     * (completed ones especially) are billed history that must keep referencing a priced subject.
     */
    fun removeSubject(subjectId: Long) {
        viewModelScope.launch {
            when (enrollment.unenroll(studentId, subjectId)) {
                EnrollmentRepository.Unenroll.HasClasses -> events.send(Event.REMOVE_BLOCKED_HAS_CLASSES)
                EnrollmentRepository.Unenroll.Ok -> Unit
            }
        }
    }

    fun setPrice(subjectId: Long, amount: Money) {
        viewModelScope.launch { enrollment.reprice(studentId, subjectId, amount) }
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

    /** Re-inserts a just-deleted payment (preserving its id, date and amount) for the undo action. */
    fun restorePayment(payment: Payment) {
        viewModelScope.launch { payments.add(payment) }
    }
}
