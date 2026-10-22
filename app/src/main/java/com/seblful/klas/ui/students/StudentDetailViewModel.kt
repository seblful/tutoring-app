package com.seblful.klas.ui.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.SessionReceipt
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.minutes
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.queries.students.StudentDetailsQuery
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.settleAfterUndoWindow
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.ui.homework.HomeworkActions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/** The student's card. */
class StudentDetailViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    private val students: StudentRepository,
    private val classes: ClassRepository,
    details: StudentDetailsQuery,
    homework: HomeworkRepository,
    private val payments: PaymentRepository,
    private val enrollment: EnrollmentRepository,
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
         * The card was archived, calling off the lessons in [cancelled] that were still planned. Shown
         * with Undo ([undoArchive]): it changed the schedule, which is another screen. The count is the
         * write's own, not the card's — a lesson marked taught from the tray while the card was open made
         * the card's figure one too many.
         */
        data class Archived(val cancelled: List<SessionReceipt>) : Event

        /** An archive was taken back, but [count] of its lessons stayed cancelled — changed, or their hour taken. */
        data class ArchiveUndoKept(val count: Int) : Event

        /** A write failed — a database error rather than a rule. */
        data object WriteFailed : Event
    }

    val events = UiEvents<Event>()

    private val homeworkActions = HomeworkActions(homework, appScope, events, Event.WriteFailed)

    val state: StateFlow<Loadable<StudentDetails>> = details.observe(studentId)
        .ready()
        .derive(this, computation, Loadable.Loading)

    /**
     * The card: [state] with its next lesson and the few listed under it, re-read every minute so a card left
     * open moves on once a lesson ends. Null until loaded. **A test that collects it must advance time by a
     * stated amount, never `advanceUntilIdle()`** — the minutes never run out.
     */
    val overview: StateFlow<StudentOverview?> = combine(state, clock.minutes()) { loaded, now ->
        loaded.valueOrNull()?.let { StudentOverview.of(it, now, clock.zone) }
    }.derive(this, computation, null)

    private fun <T> write(onWritten: suspend (T) -> Unit = {}, block: suspend () -> T) =
        appScope.write(events, Event.WriteFailed, onWritten, block)

    fun editStudent(name: String, phone: String, email: String, note: String, mode: ClassMode) {
        val current = state.value.valueOrNull()?.student ?: return
        val edited = typedStudent(name, phone, email, note, mode, onto = current) ?: return
        write { students.upsert(edited) }
    }

    /**
     * Archives or restores the student. Archiving also calls off their planned lessons — see
     * [StudentRepository.archive] — and says how many with an Undo, since that is a change to the schedule
     * the tutor is making on purpose but from another screen.
     */
    fun setArchived(archived: Boolean) {
        val current = state.value.valueOrNull()?.student ?: return
        if (!archived) return write { students.setArchived(current, archived = false) }
        write(
            onWritten = { cancelled: List<SessionReceipt> ->
                events.send(Event.Archived(cancelled))
                appScope.settleAfterUndoWindow(events, Event.WriteFailed) { classes.settleAll(cancelled) }
            },
        ) { students.archive(current) }
    }

    /** Takes [archived] back — see [StudentRepository.unarchive]. */
    fun undoArchive(archived: Event.Archived) {
        write(onWritten = { kept: Int -> if (kept > 0) events.send(Event.ArchiveUndoKept(kept)) }) {
            students.unarchive(studentId, archived.cancelled)
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

    /** Records a payment, on the day it was actually made. */
    fun addPayment(amount: Money, day: LocalDate) {
        if (amount.isZero) return
        write { payments.record(studentId, amount, day) }
    }

    /** The day the payment dialog opens on — off the same clock that will stamp the save. */
    fun paymentDay(): LocalDate = payments.today()

    /** Corrects a payment already recorded, keeping its id. */
    fun editPayment(payment: Payment, amount: Money, day: LocalDate) {
        if (amount.isZero) return
        write { payments.correct(payment, amount, day) }
    }

    fun deletePayment(payment: Payment) {
        write { payments.delete(payment) }
    }

    /** Re-inserts a just-deleted payment (preserving its id, date and amount) for the undo action. */
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
