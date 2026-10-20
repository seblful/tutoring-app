package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.HomeworkRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.days
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.ui.common.state.writeAwaited
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days

/** ne member of the group, with how much of the group they have actually attended. */
data class GroupMemberRow(val student: Student, val attended: Int)

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
}

/**  group's card: who is in it, what it has been taught, and **the whole of its lifecycle**. */
class GroupDetailViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    classes: ClassRepository,
    homework: HomeworkRepository,
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

    /** One-shot signals: a deletion refused because the group already has lessons, or a failed write. */
    enum class Event {
        DELETE_BLOCKED_HAS_LESSONS,

        /** A homework write failed — a database error rather than a rule. */
        WRITE_FAILED,
    }

    val events = UiEvents<Event>()

    private val homeworkActions = HomeworkActions(homework, appScope, events, Event.WRITE_FAILED)

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
        val attendedByStudent = groupClasses.groupingBy { it.studentId }.eachCount()
        val roster = memberIds.toSet()
        val card = GroupDetailState(
            group = group,
            subjectName = group?.subjectId?.let { id -> allSubjects.firstOrNull { it.id == id }?.name },
            // The sessions already collapsed above, rather than collapsing the same classes again.
            totals = Taught.bySessions(sessions),
            // The roster follows the same order students are listed in everywhere else: observeAll
            // has already applied it, so filtering preserves it rather than restating the rule.
            members = allStudents.filter { it.id in roster }
                .map { GroupMemberRow(it, attendedByStudent[it.id] ?: 0) },
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
        clock.days(),
    ) { base, homeworkList, today ->
        val captions = Captions.of(students = emptyList(), subjects = emptyList(), groups = listOfNotNull(base.state.group))
        base.state.copy(
            homework = HomeworkLists.of(homeworkList, base.classes, captions, today, clock.zone),
        )
    }
        .ready()
        // As the student's card: `GroupDetailState()` is an empty group, not an unread one. See [Loadable].
        .derive(this, computation, Loadable.Loading)

    /**
     * Retires the group, or brings it back. Writes straight through — there is no form here to leave
     * unsaved, which is half the reason this belongs on the card rather than on one.
     */
    fun setArchived(value: Boolean) = appScope.write(events, Event.WRITE_FAILED) {
        groups.getById(groupId)?.let { groups.setArchived(it, value) }
    }

    /**
     * Deletes the group and leaves, or reports [Event.DELETE_BLOCKED_HAS_LESSONS] — a group with
     * lessons is refused (its classes must keep naming it) and the card offers archiving instead.
     */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            appScope.writeAwaited { groups.getById(groupId)?.let { groups.delete(it) } ?: false }
                .onSuccess { if (it) onDeleted() else events.send(Event.DELETE_BLOCKED_HAS_LESSONS) }
                .onFailure { events.send(Event.WRITE_FAILED) }
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
