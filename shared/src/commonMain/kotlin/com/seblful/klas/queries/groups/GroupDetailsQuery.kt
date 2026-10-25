package com.seblful.klas.queries.groups

import com.seblful.klas.data.billing.BalanceRepository
import com.seblful.klas.data.billing.balanceOf
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.days
import com.seblful.klas.queries.homework.HomeworkLists
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Joins a group's roster, lesson history, billing and homework independently of a host. */
@SingleIn(AppScope::class)
class GroupDetailsQuery @Inject constructor(
    private val groups: GroupRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val classes: ClassRepository,
    private val homework: HomeworkRepository,
    private val balances: BalanceRepository,
    private val clock: WallClock,
) {
    private data class Base(val details: GroupDetails, val classes: List<TutoringClass>)

    fun observe(groupId: Long): Flow<GroupDetails> {
        val base = combine(
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
            val card = GroupDetails(
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
                        val present = taught.count {
                            it.attended == Attended.PRESENT || it.attended == Attended.EXPECTED
                        }
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

        return combine(
            base,
            homework.observeForGroup(groupId),
            balances.observe(),
            clock.days(),
        ) { base, homeworkList, sums, today ->
            val captions = Captions.of(
                students = emptyList(),
                subjects = emptyList(),
                groups = listOfNotNull(base.details.group),
            )
            base.details.copy(
                homework = HomeworkLists.of(homeworkList, base.classes, captions, today, clock.zone),
                members = base.details.members.map { it.copy(balance = sums.balanceOf(it.student.id).state) },
            )
        }
    }
}
