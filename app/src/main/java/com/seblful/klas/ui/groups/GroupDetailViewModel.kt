package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.RateHistoryRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.Taught
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import com.seblful.klas.ui.common.derive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
data class GroupLessonRow(val session: ClassSession, val attendees: Int, val charge: Money)

data class GroupDetailState(
    val group: Group? = null,
    /** What the group is taught, or null for a group made before subjects were fixed on the group. */
    val subjectName: String? = null,
    /** Lessons and hours taught, and what they earned — the card's stat row. */
    val totals: Taught.Totals = Taught.Totals.NONE,
    val members: List<GroupMemberRow> = emptyList(),
    val lessons: List<GroupLessonRow> = emptyList(),
    /** Every recorded change to the group's hourly rate, newest first. */
    val rateHistory: List<RateChange> = emptyList(),
)

/**
 * A group's card: who is in it, what it has been taught, and what its rate has been.
 *
 * Read-only, like the groups list — editing the group (its name, subject, rate and roster) stays in
 * [GroupFormViewModel], so there is still exactly one place a roster changes. This screen exists
 * because the form was standing in for a card: opening a group used to mean opening its edit form,
 * which answered "how do I change this?" and nothing about "how is it going?".
 *
 * **Payments and debt are deliberately absent.** Money owed is per student and spans all of their
 * lessons, so any total here would describe neither the group nor the person; the member rows link
 * through to the student cards, which is where a balance means something.
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    groups: GroupRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    classes: ClassRepository,
    rateHistory: RateHistoryRepository,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    val groupId: Long = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<GroupDetailState> = combine(
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
        GroupDetailState(
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
                    attendees = session.size,
                    charge = Billing.charged(session.classes),
                )
            },
        )
    }.combine(rateHistory.observeFor(RateOwner.GroupRate(groupId))) { state, changes ->
        state.copy(rateHistory = changes)
    }
        .derive(this, computation, GroupDetailState())
}
