package com.seblful.klas.data.homework

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** The only writer of the `homework` table. */
@SingleIn(AppScope::class)
class HomeworkRepository @Inject constructor(
    private val dao: HomeworkDao,
    private val clock: WallClock,
    private val calendar: CalendarMirror,
) {

    fun observeAll(): Flow<List<Homework>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** A student's own homework and their groups' — see `HomeworkDao.observeForStudent`. */
    fun observeForStudent(studentId: Long): Flow<List<Homework>> =
        dao.observeForStudent(studentId).map { rows -> rows.map { it.toDomain() } }

    /** A group's own homework, for its card. */
    fun observeForGroup(groupId: Long): Flow<List<Homework>> =
        dao.observeForGroup(groupId).map { rows -> rows.map { it.toDomain() } }

    /** Sets [text] for [assignee], given now — so it is attached to their next lesson from this minute. */
    suspend fun give(assignee: Assignee, text: String): Homework {
        val homework = Homework(assignee = assignee, text = text.trim(), givenAt = clock.now())
        val saved = homework.copy(id = dao.insert(homework.toEntity()))
        refreshCalendar(setOf(assignee), saved.givenAt)
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
        refreshCalendar(setOf(previous, assignee), homework.givenAt)
    }

    /** Marks [homework] done now — into the archive — or, with [done] false, current again. */
    suspend fun setDone(homework: Homework, done: Boolean) {
        dao.setDone(homework.id, if (done) clock.now().toEpochMilliseconds() else null)
        refreshCalendar(setOf(currentAssignee(homework)), homework.givenAt)
    }

    /**
     * Puts [homework]'s done state back to what it was — the undo of a swipe either way. Not [setDone]:
     * undoing «Вернуть» with that would stamp the piece done *now*, and it would come back to the top of
     * the archive rather than to where it was.
     */
    suspend fun putBack(homework: Homework) {
        dao.setDone(homework.id, homework.doneAt?.toEpochMilliseconds())
        refreshCalendar(setOf(currentAssignee(homework)), homework.givenAt)
    }

    suspend fun delete(homework: Homework) {
        val assignee = currentAssignee(homework)
        dao.deleteById(homework.id)
        refreshCalendar(setOf(assignee), homework.givenAt)
    }

    /** Puts a just-deleted [homework] back as it was, id included — the snackbar's undo. */
    suspend fun restore(homework: Homework) {
        dao.insert(homework.toEntity())
        refreshCalendar(setOf(homework.assignee), homework.givenAt)
    }

    private suspend fun currentAssignee(homework: Homework): Assignee =
        dao.getById(homework.id)?.toDomain()?.assignee ?: homework.assignee

    private suspend fun refreshCalendar(assignees: Set<Assignee>, givenAt: Instant) {
        runCatchingCancellable { calendar.refreshHomework(assignees, givenAt) }
    }
}
