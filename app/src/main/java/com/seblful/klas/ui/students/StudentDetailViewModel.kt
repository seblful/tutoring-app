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
import com.seblful.klas.format.NameOrder
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.PaymentDate
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
import com.seblful.klas.ui.common.derive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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

    /**
     * What the archive action should do, given what this student is carrying.
     *
     * The screen used to work it out: it read the balance and the upcoming count, decided whether
     * either was worth asking about, and then decided again — twenty lines further down — which parts
     * the question should name. Two spellings of one rule, in a composable, where no test reaches them.
     */
    val archiveIntent: ArchiveIntent
        get() = when {
            student == null -> ArchiveIntent.Nothing
            // Restoring costs nothing: the lessons are not coming back and the debt was never gone.
            student.archived -> ArchiveIntent.Restore
            else -> ArchiveIntent.of(balance, totals.upcoming)
        }
}

/**
 * What tapping archive/restore means here — and, when it means asking first, what the question has to
 * account for.
 *
 * Archiving costs two things, either of which is worth a confirmation: a debt the student still carries
 * leaves the reports (they cover active students only), and their planned lessons are called off (see
 * `StudentRepository.setArchived`). So the parts of the question are a **decision**, made from the state.
 * The words for them are string resources and stay on the screen.
 */
sealed interface ArchiveIntent {

    /** No student loaded yet, so there is nothing to offer. */
    data object Nothing : ArchiveIntent

    /** Archive without asking: nothing is lost that the tutor has not already seen. */
    data object Archive : ArchiveIntent

    /** Un-archive. Never asks — restoring takes nothing away. */
    data object Restore : ArchiveIntent

    /**
     * Ask first, and say what it costs.
     *
     * @param openBalance the balance that would leave the reports, or null when it is settled. Either
     *   sign counts: an over-payment is credit the tutor owes back, and it disappears from the reports
     *   just as a debt does.
     * @param upcomingLessons how many planned lessons would be called off; 0 when none would.
     */
    data class Confirm(val openBalance: Money?, val upcomingLessons: Int) : ArchiveIntent

    companion object {
        /** [Archive] when archiving costs nothing worth naming, [Confirm] with the parts that apply. */
        fun of(balance: Money, upcoming: Int): ArchiveIntent {
            val open = balance.takeUnless { it.isZero }
            return if (open == null && upcoming <= 0) Archive
            else Confirm(openBalance = open, upcomingLessons = upcoming.coerceAtLeast(0))
        }
    }
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
 * Every mutator goes through [write], so neither the scope nor the report can be forgotten by the
 * ninth copy of a tail; they were spelled out per mutator once, and none of the money path's had a
 * test.
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
    /** What "today" is, so the hour a payment lands at can be moved in a test. See `TimeModule`. */
    private val clock: Clock,
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
            subjects = NameOrder.sorted(
                priceList.mapNotNull { p ->
                    subjectsById[p.subjectId]?.let {
                        SubjectPrice(it, p.amount, historyBySubject[p.subjectId].orEmpty())
                    }
                },
            ) { it.subject.name },
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
        .derive(this, computation, StudentDetailState())

    private data class Partial(
        val student: Student?,
        val allSubjects: List<Subject>,
        val subjects: List<SubjectPrice>,
        val classes: List<TutoringClass>,
    )

    /**
     * Runs one write in the application scope, reporting a failure as [Event.WRITE_FAILED] — the
     * same tail `ScheduleViewModel.write` names, spelled once here instead of once per mutator. The
     * copies were not quite copies: two of them also answered something on success, which is what
     * [onWritten] carries — a refusal or a count coming back from the write is an event of its own,
     * and only a database error may reach [Event.WRITE_FAILED].
     */
    private fun <T> write(onWritten: suspend (T) -> Unit = {}, block: suspend () -> T) {
        appScope.launch {
            runCatchingCancellable { block() }
                .onSuccess { onWritten(it) }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    fun editStudent(name: String, phone: String, email: String, note: String, mode: ClassMode) {
        val current = state.value.student ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        write {
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

    /**
     * Archives or restores the student. Archiving also calls off their planned lessons — see
     * [StudentRepository.setArchived] — and says how many, since that is a change to the schedule the
     * tutor is making on purpose but from another screen.
     */
    fun setArchived(archived: Boolean) {
        val current = state.value.student ?: return
        write(onWritten = { cancelled -> if (cancelled > 0) events.send(Event.ARCHIVE_CANCELLED_LESSONS) }) {
            students.setArchived(current, archived)
        }
    }

    /** Adds a subject to the student with a price — [EnrollmentRepository] keeps link and price in step. */
    fun addSubject(subjectId: Long, amount: Money) {
        write { enrollment.enroll(studentId, subjectId, amount) }
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
        write(
            onWritten = { outcome ->
                when (outcome) {
                    EnrollmentRepository.Unenroll.HasClasses ->
                        events.send(Event.REMOVE_BLOCKED_HAS_CLASSES)
                    EnrollmentRepository.Unenroll.Ok -> Unit
                }
            },
        ) { enrollment.unenroll(studentId, subjectId) }
    }

    /** Re-prices a subject. The change is logged by [EnrollmentRepository], not asked for here. */
    fun setPrice(subjectId: Long, amount: Money) {
        write { enrollment.reprice(studentId, subjectId, amount) }
    }

    /**
     * Records a payment, on the day it was actually made.
     *
     * [date] is a parameter because it is the tutor's answer and not the app's: money is entered when
     * they get round to it, and «она заплатила во вторник» used to have no way in — the payment was
     * stamped `Instant.now()` and dated today whatever the truth was. The dialog defaults it to today,
     * which is right most of the time and correctable the rest.
     */
    fun addPayment(amount: Money, day: LocalDate) {
        if (amount.isZero) return
        val date = PaymentDate.instantFor(day, clock)
        write { payments.add(Payment(studentId = studentId, date = date, amount = amount)) }
    }

    /** The day the payment dialog opens on — [PaymentDate.today], off the clock that stamps the save. */
    fun paymentDay(): LocalDate = PaymentDate.today(clock)

    /**
     * Corrects a payment already recorded, keeping its id.
     *
     * A wrong amount used to mean deleting the row and entering it again — which is two writes with
     * the student's balance wrong in between, and a different payment as far as the database is
     * concerned. Money that was handed over once should stay one row.
     */
    fun editPayment(payment: Payment, amount: Money, day: LocalDate) {
        if (amount.isZero) return
        // Re-derived from the day, as recording one is: a correction that leaves the date alone still
        // re-stamps the hour, which is only visible as two payments of the same day swapping places.
        val date = PaymentDate.instantFor(day, clock)
        write { payments.update(payment.copy(amount = amount, date = date)) }
    }

    fun deletePayment(payment: Payment) {
        write { payments.delete(payment) }
    }

    /**
     * Re-inserts a just-deleted payment (preserving its id, date and amount) for the undo action.
     *
     * In the application scope like every other write here, and this one has the shortest fuse of them
     * all: the undo is offered by a snackbar, so it is tapped *while* the tutor may be leaving the card.
     */
    fun restorePayment(payment: Payment) {
        write { payments.add(payment) }
    }
}
