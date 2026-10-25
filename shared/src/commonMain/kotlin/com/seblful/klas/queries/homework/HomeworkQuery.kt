package com.seblful.klas.queries.homework

import com.seblful.klas.data.homework.HomeworkDao
import com.seblful.klas.data.lessons.ClassDao
import com.seblful.klas.data.people.GroupDao
import com.seblful.klas.data.people.StudentDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.homework.takes
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.people.NameOrder
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.days
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Resolves attachments, owner visibility and date-based rows without exposing supporting history reads. */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
class HomeworkQuery @Inject constructor(
    private val homework: HomeworkDao,
    private val classes: ClassDao,
    students: StudentDao,
    groups: GroupDao,
    private val clock: WallClock,
) {
    private data class People(val students: List<Student>, val groups: List<Group>) {
        val captions = Captions.of(students, emptyList(), groups)
        val assignees =
            NameOrder.sorted(students.filterNot { it.archived }) { it.name }.map { Assignee.Student(it.id) } +
                NameOrder.sorted(groups.filterNot { it.archived }) { it.name }.map { Assignee.Group(it.id) }
    }

    private val people = combine(students.observeAll(), groups.observeAll()) { studentRows, groupRows ->
        People(studentRows.map { it.toDomain() }, groupRows.map { it.toDomain() })
    }
    private val rosters = groups.observeAllMembers().map { rows ->
        rows.groupBy { it.groupId }.mapValues { (_, members) -> members.map { it.studentId } }
    }

    fun observe(filter: Flow<Assignee?> = flowOf(null)): Flow<HomeworkState> = combine(
        homework.observeAll().map { rows -> rows.map { it.toDomain() } }
            .withLessonsFrom(classes, clock.now() - PICKER_LOOKBACK),
        people,
        rosters,
        filter,
        clock.days(),
    ) { withLessons, people, rosters, who, today ->
        val taught = HomeworkRules.taught(withLessons.homework, people.students, people.groups)
        val shown = who?.let { HomeworkRules.narrowedTo(taught, it, rosters) } ?: taught
        HomeworkState(
            lists = HomeworkLists.of(shown, withLessons.classes, people.captions, today, clock.zone),
            filter = who,
            assignees = people.assignees,
            filterAssignees = HomeworkRules.withHomework(taught, people.assignees, rosters),
            captions = people.captions,
            classes = withLessons.classes,
        )
    }

    /** Today lists only current work attached to today's or earlier lessons, for active owners. */
    fun observeDue(): Flow<List<HomeworkRow>> = observe().map { state ->
        state.lists.current.filter { it.due == HomeworkRules.Due.TODAY || it.due == HomeworkRules.Due.OVERDUE }
    }.distinctUntilChanged()

    /** Cards retain archived owners and include group work even when this member is not booked into it. */
    fun observeForStudent(studentId: Long): Flow<HomeworkLists> =
        homework.observeForStudent(studentId).map { rows -> rows.map { it.toDomain() } }.asLists()

    fun observeForGroup(groupId: Long): Flow<HomeworkLists> =
        homework.observeForGroup(groupId).map { rows -> rows.map { it.toDomain() } }.asLists()

    private fun Flow<List<Homework>>.asLists(): Flow<HomeworkLists> = combine(
        withLessonsFrom(classes, since = null), people, clock.days(),
    ) { withLessons, people, today ->
        HomeworkLists.of(withLessons.homework, withLessons.classes, people.captions, today, clock.zone)
    }

    /** Reassignment preserves givenAt; its preview must include the historical destination. */
    suspend fun choicesFor(row: HomeworkRow): List<AssigneeChoice> {
        val people = people.first()
        val lessons = classes.getBetween(row.homework.givenAt.toEpochMilliseconds(), Long.MAX_VALUE)
            .map { it.toDomain() }
        val choices = people.assignees.map { who ->
            AssigneeChoice.of(who, people.captions.titleOf(who), lessons, row.homework.givenAt)
        }
        return if (choices.any { it.assignee == row.homework.assignee }) choices
        else listOf(AssigneeChoice(row.homework.assignee, row.title, row.lessonAt)) + choices
    }

    /** Sheets keep completed work attached so a tick stays visible. */
    fun observeAttachedTo(session: ClassSession): Flow<List<Homework>> = homework.observeAll().flatMapLatest { rows ->
        val candidates = candidatesFor(session, rows.map { it.toDomain() })
        val through = (session.start + 1.milliseconds).toEpochMilliseconds()
        classes.observeBetween(earliestGiven(candidates, session.start), through)
            .map { lessons -> HomeworkRules.attachedToLesson(session, candidates, lessons.map { it.toDomain() }) }
    }

    /** Reminders ask at firing time and name only current assignments. */
    suspend fun currentAttachedTo(session: ClassSession): List<Homework> {
        val current = candidatesFor(session, homework.getCurrent().map { it.toDomain() })
        if (current.isEmpty()) return emptyList()
        val through = (session.start + 1.milliseconds).toEpochMilliseconds()
        val lessons = classes.getBetween(earliestGiven(current, session.start), through)
            .map { it.toDomain() }
        return HomeworkRules.attachedToLesson(session, current, lessons)
    }

    internal data class CalendarRead(val attached: Map<Long, List<Homework>>, val related: List<TutoringClass>)

    /** One batched read for the mirror, including invitations whose attachment changes with these lessons. */
    internal suspend fun forCalendar(mirrored: List<TutoringClass>, changed: List<TutoringClass>): CalendarRead {
        val touched = mirrored + changed
        val current = homework.getCurrent().map { it.toDomain() }
        val affected = current.filter { item -> touched.any { item.assignee.takes(it) } }
        val lessonsOf = affected.groupBy { it.assignee }
            .mapValues { (who, theirs) -> classes.forAssignee(who, theirs.minOf { it.givenAt }) }
            .toMutableMap()
        val mirroredIds = mirrored.mapTo(mutableSetOf()) { it.id }
        val changedIds = changed.mapTo(mutableSetOf()) { it.id }
        val related = affected.filter { item -> changed.any { item.assignee.takes(it) } }.flatMap { item ->
            val theirs = lessonsOf.getValue(item.assignee)
            val next = HomeworkRules.nextLesson(item.assignee, item.givenAt, theirs.filter { it.id !in changedIds })
            theirs.filter { next != null && it.lessonId == next.lessonId && item.assignee.takes(it) }
        }.filter { it.calendarEventId != null && it.id !in mirroredIds }.distinctBy { it.id }
        // A related group invitation can also name a member's individual work unaffected by the change.
        val targets = mirrored + related
        val described = current.filter { item -> targets.any { item.assignee.takes(it) } }
        described.groupBy { it.assignee }.filterKeys { it !in lessonsOf }.forEach { (who, theirs) ->
            lessonsOf[who] = classes.forAssignee(who, theirs.minOf { it.givenAt })
        }
        val attached = described.mapNotNull { item ->
            HomeworkRules.attachedTo(item, lessonsOf.getValue(item.assignee))?.lessonId?.let { it to item }
        }.groupBy({ it.first }, { it.second })
        return CalendarRead(attached, related)
    }

    internal suspend fun refreshTargets(assignees: Set<Assignee>, givenAt: Instant): List<TutoringClass> =
        assignees.flatMap { who ->
            val theirs = classes.forAssignee(who, givenAt)
            val next = HomeworkRules.nextLesson(who, givenAt, theirs) ?: return@flatMap emptyList()
            theirs.filter { it.lessonId == next.lessonId && it.calendarEventId != null && who.takes(it) }
        }.distinctBy { it.id }

    private companion object {
        val PICKER_LOOKBACK = 14.days
    }
}

private data class WithLessons(val homework: List<Homework>, val classes: List<TutoringClass>)

/** Old current work widens only its own owner's history; new-assignment choices retain recent lessons. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<List<Homework>>.withLessonsFrom(classes: ClassDao, since: Instant?): Flow<WithLessons> =
    flatMapLatest { list ->
        val older = list.filter { !it.isDone && (since == null || it.givenAt < since) }.groupBy { it.assignee }
            .map { (who, theirs) -> classes.observeForAssignee(who, theirs.minOf { it.givenAt }) }
        val reads = listOfNotNull(
            since?.let {
                classes.observeBetween(it.toEpochMilliseconds(), Long.MAX_VALUE).map { rows ->
                    rows.map { it.toDomain() }
                }
            },
        ) + older
        if (reads.isEmpty()) flowOf(WithLessons(list, emptyList()))
        else combine(reads) { parts ->
            WithLessons(list, parts.flatMap { it }.distinctBy { it.id }.sortedBy { it.start })
        }
    }

private fun ClassDao.observeForAssignee(who: Assignee, after: Instant): Flow<List<TutoringClass>> = when (who) {
    is Assignee.Student -> observeForStudentAfter(who.studentId, after.toEpochMilliseconds())
    is Assignee.Group -> observeForGroupAfter(who.groupId, after.toEpochMilliseconds())
}.map { rows -> rows.map { it.toDomain() } }

private suspend fun ClassDao.forAssignee(who: Assignee, after: Instant): List<TutoringClass> = when (who) {
    is Assignee.Student -> forStudentAfter(who.studentId, after.toEpochMilliseconds())
    is Assignee.Group -> forGroupAfter(who.groupId, after.toEpochMilliseconds())
}.map { it.toDomain() }

private fun earliestGiven(items: List<Homework>, fallback: Instant): Long =
    (items.minOfOrNull { it.givenAt } ?: fallback).toEpochMilliseconds()

private fun candidatesFor(session: ClassSession, items: List<Homework>): List<Homework> =
    items.filter { item -> item.givenAt < session.start && session.classes.any { item.assignee.takes(it) } }
