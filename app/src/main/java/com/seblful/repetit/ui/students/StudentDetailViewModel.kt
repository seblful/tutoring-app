package com.seblful.repetit.ui.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.EnrollmentRepository
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.PaymentRepository
import com.seblful.repetit.data.repository.RateHistoryRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.Billing
import com.seblful.repetit.domain.LessonKind
import com.seblful.repetit.domain.Taught
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Payment
import com.seblful.repetit.domain.model.RateChange
import com.seblful.repetit.domain.model.RateOwner
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.TutoringClass
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.repetit.ui.common.UiEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * A subject the student takes, with its per-class price and how that price got there.
 *
 * The price is always set: a student's subjects are exactly the subjects they have a price for — the
 * same set that's bookable — so a subject can never appear here without one (see
 * [StudentDetailViewModel]).
 *
 * @param history every recorded change to [price], newest first. Empty means the rate has never
 *   moved, which the card says out loud rather than leaving blank.
 */
data class SubjectPrice(
    val subject: Subject,
    val price: Money,
    val history: List<RateChange> = emptyList(),
)

/**
 * A class plus what it takes to read it on its own: the subject's name, and — for a group lesson —
 * the group it was one of.
 *
 * @param groupName the group whose lesson this was, or null for an individual one. It is the *name*
 *   rather than a flag because "which group" is the useful half of "not individual": a student in two
 *   groups is billed a different rate by each.
 */
data class ClassRow(
    val klass: TutoringClass,
    val subjectName: String,
    val groupName: String? = null,
) {
    /** Read off the class, so a group lesson whose group row can't be found still reads as one. */
    val kind: LessonKind get() = LessonKind.of(klass)
}

data class StudentDetailState(
    val student: Student? = null,
    val balance: Money = Money.ZERO,
    /** How much has been taught and earned — the card's stat row. */
    val totals: Taught.Totals = Taught.Totals.NONE,
    val subjects: List<SubjectPrice> = emptyList(),
    val allSubjects: List<Subject> = emptyList(),
    val classes: List<ClassRow> = emptyList(),
    val payments: List<Payment> = emptyList(),
    /** How many lessons of each kind, for the lessons tab's chips. A kind with none is absent. */
    val lessonKinds: Map<LessonKind, Int> = emptyMap(),
) {
    /**
     * Whether narrowing the lesson list by kind means anything here. A student taught only one way
     * gets no chips: every chip but one would be empty, and «Все» would filter nothing.
     */
    val hasBothKinds: Boolean get() = lessonKinds.size > 1
}

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val students: StudentRepository,
    subjects: SubjectRepository,
    classes: ClassRepository,
    groups: GroupRepository,
    private val payments: PaymentRepository,
    private val enrollment: EnrollmentRepository,
    rateHistory: RateHistoryRepository,
) : ViewModel() {

    private val studentId: Long = checkNotNull(savedStateHandle["id"])

    /** One-shot UI signals, e.g. removing a subject blocked because the student has classes for it. */
    enum class Event { REMOVE_BLOCKED_HAS_CLASSES }

    val events = UiEvents<Event>()

    private val partial: Flow<Partial> = combine(
        students.observeById(studentId),
        subjects.observeAll(),
        enrollment.observePricesFor(studentId),
        classes.observeForStudent(studentId),
        // The whole student's log in one query, split per subject below — the card shows every
        // subject at once, so a query per subject would be n queries for one screen.
        rateHistory.observeForStudent(studentId),
    ) { student, allSubjects, priceList, classList, changes ->
        val subjectsById = allSubjects.associateBy { it.id }
        val historyBySubject = changes.groupBy { (it.owner as RateOwner.StudentSubject).subjectId }
        Partial(
            student = student,
            allSubjects = allSubjects,
            // A student's subjects ARE their priced subjects — one source of truth, so the list here
            // is exactly what can be booked. No "price not set" rows, no bookable-but-hidden subjects.
            subjects = priceList.mapNotNull { p ->
                subjectsById[p.subjectId]?.let {
                    SubjectPrice(it, p.amount, historyBySubject[p.subjectId].orEmpty())
                }
            }.sortedBy { it.subject.name.lowercase() },
            classes = classList,
        )
    }

    /**
     * The card, in two stages only because `combine` takes at most five typed sources.
     *
     * Groups are here for the **names** of the lessons that were a group's: a student's lesson list
     * mixes both kinds, and «Биология, 18:00» twice over — once billed at 30 and once at 20 — is a
     * puzzle until it says which of the two was the group's.
     */
    val state: StateFlow<StudentDetailState> = combine(
        partial,
        payments.observeForStudent(studentId),
        groups.observeAll(),
    ) { partial, paymentList, groupList ->
        val subjectsById = partial.allSubjects.associateBy { it.id }
        val groupNames = groupList.associate { it.id to it.name }
        StudentDetailState(
            student = partial.student,
            balance = Billing.balanceOf(studentId, partial.classes, paymentList).balance,
            totals = Taught.byStudent(partial.classes),
            subjects = partial.subjects,
            allSubjects = partial.allSubjects,
            classes = partial.classes.map { c ->
                ClassRow(
                    klass = c,
                    subjectName = subjectsById[c.subjectId]?.name ?: "",
                    groupName = c.groupId?.let { groupNames[it] },
                )
            },
            payments = paymentList,
            lessonKinds = LessonKind.tally(partial.classes),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentDetailState())

    private data class Partial(
        val student: Student?,
        val allSubjects: List<Subject>,
        val subjects: List<SubjectPrice>,
        val classes: List<TutoringClass>,
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

    /** Re-prices a subject. The change is logged by [EnrollmentRepository], not asked for here. */
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
