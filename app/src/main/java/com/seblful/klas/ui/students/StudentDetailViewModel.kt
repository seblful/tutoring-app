package com.seblful.klas.ui.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.RateHistoryRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.LessonKind
import com.seblful.klas.domain.Taught
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.klas.ui.common.UiEvents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
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

/**
 * The student's card.
 *
 * **Every mutator launches into [appScope], never `viewModelScope`** — which here holds only the
 * `stateIn` that reads. This card is where the student's *money* is entered, and each of its writes
 * is tapped in a dialog or a snackbar that closes as it is tapped: recording a payment and going
 * straight back would otherwise cancel the write between the dialog closing and the row landing, and
 * the tutor would have no way to tell that from a payment that saved. See
 * [com.seblful.klas.di.ApplicationScope] for the rule and why reads stay behind.
 *
 * A write that fails says so through [Event.WRITE_FAILED] rather than only reaching the scope's
 * crash log — a payment that silently did not record is the one failure the tutor must not miss.
 */
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
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    private val studentId: Long = checkNotNull(savedStateHandle["id"])

    /** One-shot UI signals, e.g. removing a subject blocked because the student has classes for it. */
    enum class Event {
        REMOVE_BLOCKED_HAS_CLASSES,

        /**
         * Archiving called off lessons that were still planned. Confirmed beforehand by the dialog, and
         * confirmed again here: it changed the schedule, which is another screen.
         */
        ARCHIVE_CANCELLED_LESSONS,

        /** A write failed — a database error rather than a rule. */
        WRITE_FAILED,
    }

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
    }
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentDetailState())

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
        appScope.launch {
            runCatchingCancellable {
                students.upsert(
                    current.copy(
                        name = trimmed,
                        phone = phone.trim(),
                        email = email.trim(),
                        note = note.trim(),
                        defaultMode = mode,
                    )
                )
            }.onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    /**
     * Archives or restores the student. Archiving also calls off their planned lessons — see
     * [StudentRepository.setArchived] — and says how many, since that is a change to the schedule the
     * tutor is making on purpose but from another screen.
     */
    fun setArchived(archived: Boolean) {
        val current = state.value.student ?: return
        appScope.launch {
            runCatchingCancellable { students.setArchived(current, archived) }
                .onSuccess { cancelled -> if (cancelled > 0) events.send(Event.ARCHIVE_CANCELLED_LESSONS) }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    /** Adds a subject to the student with a price — [EnrollmentRepository] keeps link and price in step. */
    fun addSubject(subjectId: Long, amount: Money) {
        appScope.launch {
            runCatchingCancellable { enrollment.enroll(studentId, subjectId, amount) }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    /**
     * Removes a subject from the student. [EnrollmentRepository] refuses (and we signal
     * [Event.REMOVE_BLOCKED_HAS_CLASSES]) when the student has any class for it, since those classes
     * (completed ones especially) are billed history that must keep referencing a priced subject.
     *
     * The refusal is a *result*, not a failure, so it is read off a successful call; only a database
     * error reaches [Event.WRITE_FAILED].
     */
    fun removeSubject(subjectId: Long) {
        appScope.launch {
            runCatchingCancellable { enrollment.unenroll(studentId, subjectId) }
                .onSuccess { outcome ->
                    when (outcome) {
                        EnrollmentRepository.Unenroll.HasClasses ->
                            events.send(Event.REMOVE_BLOCKED_HAS_CLASSES)
                        EnrollmentRepository.Unenroll.Ok -> Unit
                    }
                }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    /** Re-prices a subject. The change is logged by [EnrollmentRepository], not asked for here. */
    fun setPrice(subjectId: Long, amount: Money) {
        appScope.launch {
            runCatchingCancellable { enrollment.reprice(studentId, subjectId, amount) }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    /**
     * Records a payment, on the day it was actually made.
     *
     * [date] is a parameter because it is the tutor's answer and not the app's: money is entered when
     * they get round to it, and «она заплатила во вторник» used to have no way in — the payment was
     * stamped `Instant.now()` and dated today whatever the truth was. The dialog defaults it to today,
     * which is right most of the time and correctable the rest.
     */
    fun addPayment(amount: Money, date: Instant) {
        if (amount.isZero) return
        appScope.launch {
            runCatchingCancellable {
                payments.add(Payment(studentId = studentId, date = date, amount = amount))
            }.onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    /**
     * Corrects a payment already recorded, keeping its id.
     *
     * A wrong amount used to mean deleting the row and entering it again — which is two writes with
     * the student's balance wrong in between, and a different payment as far as the database is
     * concerned. Money that was handed over once should stay one row.
     */
    fun editPayment(payment: Payment, amount: Money, date: Instant) {
        if (amount.isZero) return
        appScope.launch {
            runCatchingCancellable { payments.update(payment.copy(amount = amount, date = date)) }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    fun deletePayment(payment: Payment) {
        appScope.launch {
            runCatchingCancellable { payments.delete(payment) }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    /**
     * Re-inserts a just-deleted payment (preserving its id, date and amount) for the undo action.
     *
     * In the application scope like every other write here, and this one has the shortest fuse of them
     * all: the undo is offered by a snackbar, so it is tapped *while* the tutor may be leaving the card.
     */
    fun restorePayment(payment: Payment) {
        appScope.launch {
            runCatchingCancellable { payments.add(payment) }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }
}
