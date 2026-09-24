package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.HomeworkRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.Attendance
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.Captions
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.HomeworkRules
import com.seblful.klas.domain.Taught
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.ready
import com.seblful.klas.ui.common.valueOrNull
import com.seblful.klas.ui.homework.AssigneeChoice
import com.seblful.klas.ui.homework.HomeworkLists
import com.seblful.klas.ui.homework.HomeworkRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * One member of the group, with how much of the group they have actually attended.
 *
 * The count is of the group's **lessons they were in**, not of their classes overall: a member's own
 * card is where their whole history lives, and this list is about the group. It is also the honest
 * answer to "who is actually coming", which a roster alone does not give — someone excluded from
 * lesson after lesson is still on the roster.
 *
 * Deliberately no balance: a member's debt spans every lesson they have ever had, group and
 * individual, so a number here would look like the group's and be neither. Money owed is the student
 * card's subject; tapping through is what this row is for.
 */
data class GroupMemberRow(val student: Student, val attended: Int)

/**
 * One of the group's lessons: the classes of a single session, plus what it came to.
 *
 * @param charge the whole lesson's recognized income — the sum over its members, since each of them
 *   is billed the group's rate separately (see `Group.price`).
 */
data class GroupLessonRow(
    val session: ClassSession,
    val charge: Money,
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
)

/**
 * A group's card: who is in it, what it has been taught, and **the whole of its lifecycle**.
 *
 * Editing the group's *contents* — name, subject, rate, roster — stays in [GroupFormViewModel], so
 * there is still exactly one place a roster changes. Retiring or removing the group does not: those
 * are lifecycle verbs and they belong on the entity's **card**, behind the overflow, which is where a
 * student's already were. They used to sit in a "danger zone" at the foot of the group's *form*,
 * meaning the same act was reached two different ways depending on which entity it was — and reaching
 * the group's meant scrolling past every candidate on the roster, fifteen of them for a large group.
 *
 * **Payments and debt are deliberately absent.** Money owed is per student and spans all of their
 * lessons, so any total here would describe neither the group nor the person; the member rows link
 * through to the student cards, which is where a balance means something.
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    classes: ClassRepository,
    private val homework: HomeworkRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: Clock,
) : ViewModel() {

    /** One-shot signals: a deletion refused because the group already has lessons, or a failed write. */
    enum class Event {
        DELETE_BLOCKED_HAS_LESSONS,

        /** A homework write failed — a database error rather than a rule. */
        WRITE_FAILED,
    }

    val events = UiEvents<Event>()

    val groupId: Long = checkNotNull(savedStateHandle["id"])

    /** The card without its homework, and the group's classes it is attached by — five sources is `combine`'s most. */
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
        val sessions = GroupRules.sessions(groupClasses).reversed()
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
                    charge = Billing.charged(session.classes),
                )
            },
        )
        Base(card, groupClasses)
    }

    val state: StateFlow<Loadable<GroupDetailState>> = combine(
        base,
        homework.observeForGroup(groupId),
    ) { base, homeworkList ->
        val captions = Captions.of(students = emptyList(), subjects = emptyList(), groups = listOfNotNull(base.state.group))
        base.state.copy(
            homework = HomeworkRow.of(
                HomeworkRules.lists(homeworkList, base.classes),
                captions,
                clock.instant().atZone(clock.zone).toLocalDate(),
                clock.zone,
            ),
        )
    }
        .ready()
        // As the student's card: `GroupDetailState()` is an empty group, not an unread one. See [Loadable].
        .derive(this, computation, Loadable.Loading)

    /**
     * Retires the group, or brings it back. Writes straight through — there is no form here to leave
     * unsaved, which is half the reason this belongs on the card rather than on one.
     */
    fun setArchived(value: Boolean) {
        viewModelScope.launch {
            groups.getById(groupId)?.let { groups.setArchived(it, value) }
        }
    }

    /**
     * Deletes the group and leaves, or reports [Event.DELETE_BLOCKED_HAS_LESSONS] — a group with
     * lessons is refused (its classes must keep naming it) and the card offers archiving instead.
     *
     * Reported through [events] rather than a `(Boolean) -> Unit`, so a refusal here reaches the tutor
     * the same way every other refusal in the app does.
     */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val group = groups.getById(groupId)
            if (group != null && groups.delete(group)) onDeleted()
            else events.send(Event.DELETE_BLOCKED_HAS_LESSONS)
        }
    }

    /**
     * Who homework set from this card is for — the group, **as of now**, with the lesson it would be
     * attached to. Taken when the dialog opens, as the student card's is. Null before the card has loaded.
     */
    fun homeworkChoice(): AssigneeChoice? {
        val loaded = state.value.valueOrNull() ?: return null
        val group = loaded.group ?: return null
        val assignee = Assignee.Group(groupId)
        val classes = loaded.lessons.flatMap { it.session.classes }
        return AssigneeChoice(
            assignee = assignee,
            name = group.name,
            nextLessonAt = HomeworkRules.nextLesson(assignee, clock.instant(), classes)?.start,
        )
    }

    /** Sets homework for the whole group — attached to its next lesson from now. */
    fun giveHomework(text: String) = write { homework.give(Assignee.Group(groupId), text) }

    /** Corrects [item]'s text; it stays the group's. */
    fun editHomework(item: Homework, text: String) = write { homework.edit(item, item.assignee, text) }

    fun setHomeworkDone(item: Homework, done: Boolean) = write { homework.setDone(item, done) }

    /** Undoes a swipe, [item]'s done state back as it was — see `HomeworkRepository.putBack`. */
    fun putHomeworkBack(item: Homework) = write { homework.putBack(item) }

    fun deleteHomework(item: Homework) = write { homework.delete(item) }

    fun restoreHomework(item: Homework) = write { homework.restore(item) }

    /** In [appScope], as every homework write: a swipe or a dialog closing must not cut it short. */
    private fun write(block: suspend () -> Unit) {
        appScope.launch {
            runCatchingCancellable { block() }.onFailure { events.send(Event.WRITE_FAILED) }
        }
    }
}
