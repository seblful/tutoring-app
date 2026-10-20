package com.seblful.klas.data.homework

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.homework.takes
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** omework and the lessons it is attached to — the only writer of the `homework` table. */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
class HomeworkRepository @Inject constructor(
    private val dao: HomeworkDao,
    private val classes: ClassRepository,
    private val clock: WallClock,
    private val calendar: CalendarMirror,
) {

    /** Every piece of homework, and the classes [HomeworkRules] attaches the current ones by. */
    data class WithLessons(val homework: List<Homework>, val classes: List<TutoringClass>)

    fun observeAll(): Flow<List<Homework>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** A student's own homework and their groups' — see `HomeworkDao.observeForStudent`. */
    fun observeForStudent(studentId: Long): Flow<List<Homework>> =
        dao.observeForStudent(studentId).map { rows -> rows.map { it.toDomain() } }

    /** A group's own homework, for its card. */
    fun observeForGroup(groupId: Long): Flow<List<Homework>> =
        dao.observeForGroup(groupId).map { rows -> rows.map { it.toDomain() } }

    /**
     * Every piece of homework, with the classes from **the earliest current one's giving — or [since], if
     * that is earlier — onwards, open-ended**: every lesson any of them could be attached to.
     */
    fun observeWithLessons(since: Instant): Flow<WithLessons> =
        observeAll().withLessonsFrom { earliestCurrent(it, since) }

    /**
     * A student's homework and their groups' ([observeForStudent]), with the lessons it is attached by —
     * **everybody's**, as the tab reads them. A group's homework is attached to the group's next lesson,
     * and a member left out of one (`ClassForm.excludedMemberIds`) has no class of their own in it; read
     * off their own classes, the card would put it on a different lesson than the tab.
     */
    fun observeForStudentWithLessons(studentId: Long): Flow<WithLessons> =
        observeForStudent(studentId).withLessonsFrom { earliestCurrent(it, Forever) }

    /**
     * The homework attached to [session] ([HomeworkRules.attachedToLesson]) for as long as it is watched —
     * the lesson's sheet, where a tick has to redraw the one just ticked.
     */
    fun observeAttachedTo(session: ClassSession): Flow<List<Homework>> =
        observeAll().flatMapLatest { all ->
            val candidates = candidatesFor(session, all)
            classes.observeBetween(earliestGiven(candidates, session.start), session.start.plus(1.milliseconds))
                .map { lessons -> HomeworkRules.attachedToLesson(session, candidates, lessons) }
        }

    /**
     * The **current** homework attached to [session], read once — for its reminder, which asks as it posts.
     */
    suspend fun currentAttachedTo(session: ClassSession): List<Homework> {
        val current = candidatesFor(session, dao.getAll().map { it.toDomain() }).filterNot { it.isDone }
        if (current.isEmpty()) return emptyList()
        val lessons = classes.getBetween(earliestGiven(current, session.start), session.start.plus(1.milliseconds))
        return HomeworkRules.attachedToLesson(session, current, lessons)
    }

    private fun Flow<List<Homework>>.withLessonsFrom(start: (List<Homework>) -> Instant): Flow<WithLessons> =
        flatMapLatest { list -> classes.observeBetween(start(list), Forever).map { WithLessons(list, it) } }

    private fun candidatesFor(session: ClassSession, homework: List<Homework>): List<Homework> =
        homework.filter { hw -> hw.givenAt < session.start && session.classes.any { hw.assignee.takes(it) } }

    /** Sets [text] for [assignee], given now — so it is attached to their next lesson from this minute. */
    suspend fun give(assignee: Assignee, text: String): Homework {
        val homework = Homework(assignee = assignee, text = text.trim(), givenAt = clock.now())
        val saved = homework.copy(id = dao.insert(homework.toEntity()))
        refreshCalendar(setOf(assignee))
        return saved
    }

    /**
     * Corrects [homework]'s text or who it is for, preserving when it was given and whether it is done.
     * Text edits leave its derived lesson unchanged; changing [assignee] finds their next lesson after
     * the original given time. See `HomeworkDao` for why this is not a whole-row write.
     */
    suspend fun edit(homework: Homework, assignee: Assignee, text: String) {
        val previous = dao.getById(homework.id)?.toDomain()?.assignee ?: homework.assignee
        val row = homework.copy(assignee = assignee).toEntity()
        dao.edit(homework.id, row.studentId, row.groupId, text.trim())
        refreshCalendar(setOf(previous, assignee))
    }

    /** Marks [homework] done now — into the archive — or, with [done] false, current again. */
    suspend fun setDone(homework: Homework, done: Boolean) {
        dao.setDone(homework.id, if (done) clock.now().toEpochMilliseconds() else null)
        refreshCalendar(setOf(currentAssignee(homework)))
    }

    /**
     * Puts [homework]'s done state back to what it was — the undo of a swipe either way. Not [setDone]:
     * undoing «Вернуть» with that would stamp the piece done *now*, and it would come back to the top of
     * the archive rather than to where it was.
     */
    suspend fun putBack(homework: Homework) {
        dao.setDone(homework.id, homework.doneAt?.toEpochMilliseconds())
        refreshCalendar(setOf(currentAssignee(homework)))
    }

    suspend fun delete(homework: Homework) {
        val assignee = currentAssignee(homework)
        dao.deleteById(homework.id)
        refreshCalendar(setOf(assignee))
    }

    /** Puts a just-deleted [homework] back as it was, id included — the snackbar's undo. */
    suspend fun restore(homework: Homework) {
        dao.insert(homework.toEntity())
        refreshCalendar(setOf(homework.assignee))
    }

    private suspend fun currentAssignee(homework: Homework): Assignee =
        dao.getById(homework.id)?.toDomain()?.assignee ?: homework.assignee

    private suspend fun refreshCalendar(assignees: Set<Assignee>) {
        runCatchingCancellable { calendar.refreshHomework(assignees) }
    }

    private fun earliestCurrent(homework: List<Homework>, since: Instant): Instant =
        homework.filterNot { it.isDone }.minOfOrNull { it.givenAt }?.coerceAtMost(since) ?: since

    private fun earliestGiven(homework: List<Homework>, fallback: Instant): Instant =
        homework.minOfOrNull { it.givenAt } ?: fallback

    private companion object {
        /** The open end of a window forward — the next lesson can be booked however far ahead. */
        val Forever: Instant = Instant.fromEpochMilliseconds(Long.MAX_VALUE)
    }
}
