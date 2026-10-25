package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.seblful.klas.data.billing.BalanceRepository
import com.seblful.klas.data.billing.balanceOf
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.lesson.CardLessons
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.days
import com.seblful.klas.format.minutes
import com.seblful.klas.lessons.ArchiveChange
import com.seblful.klas.lessons.LessonChanges
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkLists
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.ui.common.state.writeAwaited
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
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * A member as the group's card lists them: of the group's taught lessons they were booked into ([held]),
 * how many they were at ([present]) — what a tutor checks about a group — and where their money stands.
 */
data class GroupMemberRow(
    val student: Student,
    val held: Int,
    val present: Int,
    val balance: Balance = Balance.Settled,
)

/**
 * One of the group's lessons: the classes of a single session, plus what it came to.
 *
 * @param charge the whole lesson's recognized income — the sum over its members, since each of them
 * is billed the group's rate separately (see `Group.price`) — or null where it billed nobody. See
 * `Billing.billedOrNull`.
 */
data class GroupLessonRow(
    val session: ClassSession,
    val charge: Money?,
) {
    /**
     * How the members read on this row.
     *
     * The row used to carry `members` and `present` as two counts and leave the screen to work out which
     * sentence they made. [ClassSession.attendance] is that decision, named once and tested.
     */
    val attendance: Attendance get() = session.attendance
}

data class GroupDetailState(
    val group: Group? = null,
    /** What the group is taught, or null for a group made before subjects were fixed on the group. */
    val subjectName: String? = null,
    /** Lessons and hours taught, and what they earned — the card's stat row. */
    val totals: Taught.Totals = Taught.Totals.NONE,
    val members: List<GroupMemberRow> = emptyList(),
    val lessons: List<GroupLessonRow> = emptyList(),
    /** The group's own homework, current and done — the card's homework view. See [HomeworkRules.lists]. */
    val homework: HomeworkLists = HomeworkLists(current = emptyList(), archive = emptyList()),
) {
    /**
     * Whether «Удалить» is offered at all. A group is archive-only once it has lessons — they are billed
     * history — so the menu used to ask «Удалить группу?» and then refuse the answer it had asked for.
     */
    val canDelete: Boolean get() = lessons.isEmpty()

    /** How many planned lessons archiving would call off — asked about first when there are any. */
    val plannedLessons: Int get() = lessons.count { it.session.status == LessonStatus.SCHEDULED }

    /** The lesson the tutor next teaches the group — see [CardLessons.next]. */
    fun nextLesson(now: Instant): GroupLessonRow? = CardLessons.next(lessons, now) { it.session.first }

    /** The few lessons the card shows under its lessons heading — see [CardLessons.preview]. */
    fun lessonPreview(now: Instant, limit: Int): List<GroupLessonRow> =
        CardLessons.preview(lessons, now, limit) { it.session.first }
}

/** A group's card: who is in it, what it has been taught, and **the whole of its lifecycle**. */
class GroupDetailViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    private val classes: ClassRepository,
    homework: HomeworkRepository,
    balances: BalanceRepository,
    lessonChanges: LessonChanges,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: WallClock,
) : ViewModel() {

    /** Hands over the screen's navigation arguments as the [SavedStateHandle]; the rest is injected. */
    @AssistedFactory
    @ViewModelAssistedFactoryKey(GroupDetailViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): GroupDetailViewModel = create(extras.createSavedStateHandle())

        fun create(@Assisted savedStateHandle: SavedStateHandle): GroupDetailViewModel
    }

    /** One-shot signals: a refused deletion, the lessons archiving called off, or a failed write. */
    sealed interface Event {
        data object DeleteBlockedHasLessons : Event

        /**
         * The card was archived, calling off the lessons in [archive] that were still planned. Shown
         * with Undo ([undoArchive]): it changed the schedule, which is another screen. The count is the
         * write's own, not the card's — a lesson marked taught from the tray while the card was open made
         * the card's figure one too many.
         */
        data class Archived(val archive: ArchiveChange) : Event

        /** An archive was taken back, but [count] of its lessons stayed cancelled — changed, or their hour taken. */
        data class ArchiveUndoKept(val count: Int) : Event

        /** A write failed — a database error rather than a rule. */
        data object WriteFailed : Event
    }

    val events = UiEvents<Event>()

    private val changes = lessonChanges.inScope(appScope) { events.send(Event.WriteFailed) }

    private val homeworkActions = HomeworkActions<Event>(homework, appScope, events, Event.WriteFailed)

    val groupId: Long = checkNotNull(savedStateHandle["id"])

    private data class Base(val state: GroupDetailState, val classes: List<TutoringClass>)

    private val base = combine(
        groups.observeById(groupId),
        groups.observeMemberIds(groupId),
        // Every student, not just active ones: an archived member still explains a past lesson.
        students.observeAll(),
        subjects.observeAll(),
        classes.observeForGroup(groupId),
    ) { group, memberIds, allStudents, allSubjects, groupClasses ->
        // Sessions, newest first: one lesson is n member classes sharing a group and a start, and the
        // card counts lessons the tutor taught, not classes billed (see Taught.byGroup).
        val sessions = SessionRules.sessions(groupClasses).reversed()
        val taughtByStudent = groupClasses.filter { it.status == LessonStatus.COMPLETED }.groupBy { it.studentId }
        val roster = memberIds.toSet()
        val card = GroupDetailState(
            group = group,
            subjectName = group?.subjectId?.let { id -> allSubjects.firstOrNull { it.id == id }?.name },
            // The sessions already collapsed above, rather than collapsing the same classes again.
            totals = Taught.bySessions(sessions),
            // The roster follows the same order students are listed in everywhere else: observeAll
            // has already applied it, so filtering preserves it rather than restating the rule.
            members = allStudents.filter { it.id in roster }
                .map { student ->
                    val taught = taughtByStudent[student.id].orEmpty()
                    // Not marked absent: a lesson marked taught without a roll call happened for everyone in it.
                    val present = taught.count { it.attended == Attended.PRESENT || it.attended == Attended.EXPECTED }
                    GroupMemberRow(student, held = taught.size, present = present)
                },
            lessons = sessions.map { session ->
                GroupLessonRow(
                    session = session,
                    charge = Billing.billedOrNull(session.classes),
                )
            },
        )
        Base(card, groupClasses)
    }

    /**
     * The card. Its homework is due as of [days], so a card left open overnight moves tomorrow's onto
     * today. **A test that collects this must advance time by a stated amount, never
     * `advanceUntilIdle()`** — see [days].
     */
    val state: StateFlow<Loadable<GroupDetailState>> = combine(
        base,
        homework.observeForGroup(groupId),
        balances.observe(),
        clock.days(),
    ) { base, homeworkList, sums, today ->
        val captions = Captions.of(
            students = emptyList(),
            subjects = emptyList(),
            groups = listOfNotNull(base.state.group),
        )
        base.state.copy(
            homework = HomeworkLists.of(homeworkList, base.classes, captions, today, clock.zone),
            members = base.state.members.map { it.copy(balance = sums.balanceOf(it.student.id).state) },
        )
    }
        .ready()
        // As the student's card: `GroupDetailState()` is an empty group, not an unread one. See [Loadable].
        .derive(this, computation, Loadable.Loading)

    /**
     * The card: [state] with its next lesson and the few listed under it, re-read every minute so a card left
     * open moves on once a lesson ends. Null until loaded. **A test that collects it must advance time by a
     * stated amount, never `advanceUntilIdle()`** — the minutes never run out.
     */
    val overview: StateFlow<GroupOverview?> = combine(state, clock.minutes()) { loaded, now ->
        loaded.valueOrNull()?.let { GroupOverview.of(it, now, clock.zone) }
    }.derive(this, computation, null)

    /**
     * This minute, for the lessons listed here: one over and unmarked reads «Не отмечено», as on the schedule,
     * not «Запланировано». See `lessonBadgeLabel`.
     */
    val minute: StateFlow<Instant> = clock.minutes().derive(this, computation, clock.now())

    /**
     * Retires the group, or brings it back. Writes straight through — there is no form here to leave
     * unsaved, which is half the reason this belongs on the card rather than on one. Archiving is said
     * with an Undo, as a student's is.
     */
    fun setArchived(value: Boolean) {
        if (!value) {
            return appScope.write(events, Event.WriteFailed) {
                groups.getById(groupId)?.let { groups.setArchived(it, archived = false) }
            }
        }
        appScope.write(
            events,
            Event.WriteFailed,
            onWritten = { archive: ArchiveChange? ->
                if (archive != null) events.send(Event.Archived(archive))
            },
        ) { groups.getById(groupId)?.let { changes.archive(it) } }
    }

    /** Takes [archived] back — see [LessonChanges.Writer.undoArchive]. */
    fun undoArchive(archived: Event.Archived) = appScope.write(
        events,
        Event.WriteFailed,
        onWritten = { kept: Int -> if (kept > 0) events.send(Event.ArchiveUndoKept(kept)) },
    ) { changes.undoArchive(archived.archive) }

    /**
     * Deletes the group and leaves, or reports [Event.DeleteBlockedHasLessons] — a group with
     * lessons is refused (its classes must keep naming it) and the card offers archiving instead.
     */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            appScope.writeAwaited { groups.getById(groupId)?.let { groups.delete(it) } ?: false }
                .onSuccess { if (it) onDeleted() else events.send(Event.DeleteBlockedHasLessons) }
                .onFailure { events.send(Event.WriteFailed) }
        }
    }

    /**
     * Who homework set from this card is for — the group, **as of now**, with the lesson it would be
     * attached to. Taken when the dialog opens, as the student card's is. Null before the card has loaded.
     */
    fun homeworkChoice(): AssigneeChoice? {
        val loaded = state.value.valueOrNull() ?: return null
        val group = loaded.group ?: return null
        val classes = loaded.lessons.flatMap { it.session.classes }
        return AssigneeChoice.of(Assignee.Group(groupId), group.name, classes, clock.now())
    }

    /** Sets homework for the whole group — attached to its next lesson from now. */
    fun giveHomework(text: String) = homeworkActions.give(Assignee.Group(groupId), text)

    /** Corrects [item]'s text; it stays the group's. */
    fun editHomework(item: Homework, text: String) = homeworkActions.edit(item, item.assignee, text)

    fun setHomeworkDone(item: Homework, done: Boolean) = homeworkActions.setDone(item, done)

    /** Undoes a swipe, [item]'s done state back as it was — see `HomeworkRepository.putBack`. */
    fun putHomeworkBack(item: Homework) = homeworkActions.putBack(item)

    fun deleteHomework(item: Homework) = homeworkActions.delete(item)

    fun restoreHomework(item: Homework) = homeworkActions.restore(item)
}
