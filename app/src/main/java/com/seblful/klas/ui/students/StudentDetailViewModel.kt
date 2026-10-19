package com.seblful.klas.ui.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.HomeworkRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.RateHistoryRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.people.NameOrder
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.days
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.ui.homework.AssigneeChoice
import com.seblful.klas.ui.homework.HomeworkActions
import com.seblful.klas.ui.homework.HomeworkLists
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

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

    /** The amount at the row's trailing edge, or null where the lesson billed them nothing. See [Billing.billedOrNull]. */
    val shownCharge: Money? get() = Billing.billedOrNull(listOf(klass))
}

data class StudentDetailState(
    val student: Student? = null,
    /** Where the student stands — the case, not the signed amount. See [Balance]. */
    val balance: Balance = Balance.Settled,
    /** How much has been taught and earned — the card's stat row. */
    val totals: Taught.Totals = Taught.Totals.NONE,
    val subjects: List<SubjectPrice> = emptyList(),
    val allSubjects: List<Subject> = emptyList(),
    val classes: List<ClassRow> = emptyList(),
    val payments: List<Payment> = emptyList(),
    /** How many lessons of each kind, for the lessons tab's chips. A kind with none is absent. */
    val lessonKinds: Map<LessonKind, Int> = emptyMap(),
    /**
     * Their homework and their groups', current and done — the card's homework view, and the one place an
     * archived student's is still listed (the «Задания» tab leaves them out). See [HomeworkRules.lists].
     */
    val homework: HomeworkLists = HomeworkLists(current = emptyList(), archive = emptyList()),
) {
    /**
     * Whether narrowing the lesson list by kind means anything here. A student taught only one way
     * gets no chips: every chip but one would be empty, and «Все» would filter nothing.
     */
    val hasBothKinds: Boolean get() = lessonKinds.size > 1

    /** The subjects that can still be added: every subject not already priced for them. */
    val addableSubjects: List<Subject>
        get() {
            val priced = subjects.mapTo(mutableSetOf()) { it.subject.id }
            return allSubjects.filter { it.id !in priced }
        }

    /**
     * The lessons tab's rows under [filter].
     *
     * A filter that isn't on screen doesn't apply. The chips come and go with the data — deleting a
     * student's last group lesson from the schedule takes them away — and a selection left behind would go
     * on hiding their remaining lessons with nothing left to say why, or to undo it.
     */
    fun lessonsShown(filter: LessonFilter): List<ClassRow> =
        if (hasBothKinds) filter.accepted(classes) { it.kind } else classes

    /**
     * When they last paid, or null if they never have — the line under the balance. It used to repeat the
     * balance in words («Долг 45 BYN» under «Долг / 45 BYN»); when the money last came in is the thing the
     * number does not say, and what a tutor deciding whether to chase it wants next.
     */
    val lastPaymentAt: Instant? get() = payments.maxOfOrNull { it.date }

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
     * @param openBalance the balance that would leave the reports, or null when it is [Balance.Settled].
     *   Either case counts: an over-payment is credit the tutor owes back, and it disappears from the
     *   reports just as a debt does — which is why this is "not settled" rather than "owes".
     * @param upcomingLessons how many planned lessons would be called off; 0 when none would.
     */
    data class Confirm(val openBalance: Balance?, val upcomingLessons: Int) : ArchiveIntent

    companion object {
        /** [Archive] when archiving costs nothing worth naming, [Confirm] with the parts that apply. */
        fun of(balance: Balance, upcoming: Int): ArchiveIntent {
            val open = balance.takeIf { it != Balance.Settled }
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
 * A write that fails says so through [Event.WriteFailed] — a payment that silently did not record is
 * the one failure the tutor must not miss. Every mutator goes through [write] (the homework ones through
 * [HomeworkActions], over the same tail), so neither the scope nor the report can be forgotten by the
 * ninth copy of a tail; they were spelled out per mutator once, and none of the money path's had a
 * test.
 */
class StudentDetailViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    private val students: StudentRepository,
    subjects: SubjectRepository,
    private val classes: ClassRepository,
    groups: GroupRepository,
    homework: HomeworkRepository,
    private val payments: PaymentRepository,
    private val enrollment: EnrollmentRepository,
    rateHistory: RateHistoryRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: WallClock,
) : ViewModel() {

    /** Hands over the screen's navigation arguments as the [SavedStateHandle]; the rest is injected. */
    @AssistedFactory
    @ViewModelAssistedFactoryKey(StudentDetailViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): StudentDetailViewModel = create(extras.createSavedStateHandle())

        fun create(@Assisted savedStateHandle: SavedStateHandle): StudentDetailViewModel
    }

    private val studentId: Long = checkNotNull(savedStateHandle["id"])

    /** One-shot UI signals, e.g. removing a subject blocked because the student has classes for it. */
    sealed interface Event {
        data object RemoveBlockedHasClasses : Event

        /**
         * Archiving called off [count] lessons that were still planned. Confirmed beforehand by the
         * dialog, and confirmed again here: it changed the schedule, which is another screen. The count
         * is the write's own, not the card's — a lesson marked taught from the tray while the card was
         * open made the card's figure one too many.
         */
        data class ArchiveCancelledLessons(val count: Int) : Event

        /** A write failed — a database error rather than a rule. */
        data object WriteFailed : Event
    }

    val events = UiEvents<Event>()

    private val homeworkActions = HomeworkActions(homework, appScope, events, Event.WriteFailed)

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
        // `as?`, not `as`: `price_history` is **one table for two kinds of rate** discriminated by
        // `RateOwner`, and `observeForStudent` selects on `studentId` alone. A hard cast therefore rested
        // on no row of a student's ever carrying a group id — a property of the queries as they stand
        // today rather than of the schema, and a `ClassCastException` inside a `Flow` on the card that
        // shows a student's money if it ever stopped holding. A row that is not a student's price is not
        // this card's history, so it is skipped.
        val historyBySubject = changes
            .mapNotNull { change -> (change.owner as? RateOwner.StudentSubject)?.subjectId?.to(change) }
            .groupBy({ it.first }, { it.second })
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
     *
     * Homework is due as of [days], so a card left open overnight moves tomorrow's onto today. **A test
     * that collects this must advance time by a stated amount, never `advanceUntilIdle()`** — see [days].
     */
    val state: StateFlow<Loadable<StudentDetailState>> = combine(
        partial,
        payments.observeForStudent(studentId),
        groups.observeAll(),
        homework.observeForStudentWithLessons(studentId),
        clock.days(),
    ) { partial, paymentList, groupList, withLessons, today ->
        // The one student is here only to title their homework; a lesson row never names them. See [Captions].
        val captions = Captions.of(
            students = listOfNotNull(partial.student),
            subjects = partial.allSubjects,
            groups = groupList,
        )
        StudentDetailState(
            student = partial.student,
            balance = Billing.balanceOf(studentId, partial.classes, paymentList).state,
            totals = Taught.byStudent(partial.classes),
            subjects = partial.subjects,
            allSubjects = partial.allSubjects,
            classes = partial.classes.map { c ->
                ClassRow(klass = c, subjectName = captions.subjectOf(c), groupName = captions.groupOf(c))
            },
            payments = paymentList,
            lessonKinds = LessonKind.tally(partial.classes),
            // Attached by everybody's lessons, not theirs: a member left out of a group lesson has no class
            // in it, and the group's homework is still that lesson's — see `observeForStudentWithLessons`.
            homework = HomeworkLists.of(withLessons.homework, withLessons.classes, captions, today, clock.zone),
        )
    }
        .ready()
        // `StudentDetailState()` used to stand in, which is a student with no name, no debt and no
        // lessons — so the card opened stating zeroes about somebody before reading them. See [Loadable].
        .derive(this, computation, Loadable.Loading)

    private data class Partial(
        val student: Student?,
        val allSubjects: List<Subject>,
        val subjects: List<SubjectPrice>,
        val classes: List<TutoringClass>,
    )

    /**
     * One write in the application scope, a failure reported as [Event.WriteFailed] — the shared tail
     * (`ui/common/state/Writes.kt`) with this card's failure filled in. [onWritten] is for the writes that answer
     * something on success: a refusal or a count coming back is an event of its own, and only a database
     * error may reach [Event.WriteFailed].
     */
    private fun <T> write(onWritten: suspend (T) -> Unit = {}, block: suspend () -> T) =
        appScope.write(events, Event.WriteFailed, onWritten, block)

    fun editStudent(name: String, phone: String, email: String, note: String, mode: ClassMode) {
        val current = state.value.valueOrNull()?.student ?: return
        val edited = typedStudent(name, phone, email, note, mode, onto = current) ?: return
        write { students.upsert(edited) }
    }

    /**
     * Archives or restores the student. Archiving also calls off their planned lessons — see
     * [StudentRepository.setArchived] — and says how many, since that is a change to the schedule the
     * tutor is making on purpose but from another screen.
     */
    fun setArchived(archived: Boolean) {
        val current = state.value.valueOrNull()?.student ?: return
        write(onWritten = { cancelled -> if (cancelled > 0) events.send(Event.ArchiveCancelledLessons(cancelled)) }) {
            students.setArchived(current, archived)
        }
    }

    /** Adds a subject to the student with a price — [EnrollmentRepository] keeps link and price in step. */
    fun addSubject(subjectId: Long, amount: Money) {
        write { enrollment.enroll(studentId, subjectId, amount) }
    }

    /**
     * Removes a subject from the student. [EnrollmentRepository] refuses (and we signal
     * [Event.RemoveBlockedHasClasses]) when the student has any class for it, since those classes
     * (completed ones especially) are billed history that must keep referencing a priced subject.
     *
     * The refusal is a *result*, not a failure, so it is read off a successful call; only a database
     * error reaches [Event.WriteFailed].
     */
    fun removeSubject(subjectId: Long) {
        write(
            onWritten = { outcome ->
                when (outcome) {
                    EnrollmentRepository.Unenroll.HasClasses ->
                        events.send(Event.RemoveBlockedHasClasses)
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
        write { payments.record(studentId, amount, day) }
    }

    /** The day the payment dialog opens on — off the same clock that will stamp the save. */
    fun paymentDay(): LocalDate = payments.today()

    /**
     * Corrects a payment already recorded, keeping its id.
     *
     * A wrong amount used to mean deleting the row and entering it again — which is two writes with
     * the student's balance wrong in between, and a different payment as far as the database is
     * concerned. Money that was handed over once should stay one row.
     */
    fun editPayment(payment: Payment, amount: Money, day: LocalDate) {
        if (amount.isZero) return
        write { payments.correct(payment, amount, day) }
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
        write { payments.restore(payment) }
    }

    /**
     * Who homework set from this card is for — this student, **as of now**, with the lesson it would be
     * attached to. Taken when the dialog opens, since that lesson moves on as the day does. Null before the
     * card has loaded.
     */
    fun homeworkChoice(): AssigneeChoice? {
        val loaded = state.value.valueOrNull() ?: return null
        val student = loaded.student ?: return null
        return AssigneeChoice.of(
            Assignee.Student(studentId), student.name, loaded.classes.map { it.klass }, clock.now(),
        )
    }

    /** Sets homework for this student — attached to their next lesson from now. */
    fun giveHomework(text: String) {
        homeworkActions.give(Assignee.Student(studentId), text)
    }

    /** Corrects [item]'s text; who it is for is not changed from here (the card is one student's). */
    fun editHomework(item: Homework, text: String) {
        homeworkActions.edit(item, item.assignee, text)
    }

    fun setHomeworkDone(item: Homework, done: Boolean) {
        homeworkActions.setDone(item, done)
    }

    /** Undoes a swipe, [item]'s done state back as it was — see `HomeworkRepository.putBack`. */
    fun putHomeworkBack(item: Homework) {
        homeworkActions.putBack(item)
    }

    fun deleteHomework(item: Homework) {
        homeworkActions.delete(item)
    }

    /** Puts back a just-deleted piece of homework, for the undo — like [restorePayment]. */
    fun restoreHomework(item: Homework) {
        homeworkActions.restore(item)
    }
}
