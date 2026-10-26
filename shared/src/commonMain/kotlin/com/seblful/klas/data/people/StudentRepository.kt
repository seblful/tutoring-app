package com.seblful.klas.data.people

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.SessionReceipt
import com.seblful.klas.data.lessons.UndoResult
import com.seblful.klas.data.local.upsertedId
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.NameOrder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
class StudentRepository @Inject constructor(
    private val dao: StudentDao,

    private val classes: ClassRepository,

    private val calendar: CalendarMirror,
) {
    fun observeActive(): Flow<List<Student>> =
        dao.observeActive().map { list -> NameOrder.sorted(list.map { it.toDomain() }) { it.name } }

    /** [observeActive], read once — for a caller with no screen to keep current, such as the debt digest. */
    suspend fun getActive(): List<Student> = NameOrder.sorted(dao.getActive().map { it.toDomain() }) { it.name }

    /** Every student, archived ones last — they stay listed, out of the way of the active roster. */
    fun observeAll(): Flow<List<Student>> = dao.observeAll().map { list ->
        NameOrder.sortedArchivedLast(list.map { it.toDomain() }, archived = { it.archived }, name = { it.name })
    }

    fun observeById(id: Long): Flow<Student?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): Student? = dao.getById(id)?.toDomain()

    /** Saves [student] and refreshes what their mirrored events say about them. */
    suspend fun upsert(student: Student): Long {
        val before = dao.getById(student.id)
        val row = student.toEntity()
        // An edit answers -1 (see [upsertedId]) — and an edit is exactly the case that needs the
        // refresh at all.
        val saved = upsertedId(dao.upsert(row), student.id)
        // Their events show only the name (in the title) and the address (as the guest).
        if (before != null && (before.name != row.name || before.email != row.email)) {
            calendar.refreshForStudent(saved, previousEmail = before.email.takeIf { it != row.email })
        }
        return saved
    }

    /**
     * Archives or restores [student], and returns how many of their planned lessons archiving called
     * off (zero when restoring).
     */
    suspend fun setArchived(student: Student, archived: Boolean): Int {
        val cancelled = if (archived) classes.cancelScheduledFor(student.id).size else 0
        dao.upsert(student.copy(archived = archived).toEntity())
        return cancelled
    }

    /**
     * Archives [student] undoably: their planned lessons are called off as by [setArchived], and the
     * receipts returned take them back through [unarchive]. LessonChanges owns their settlement.
     */
    internal suspend fun archive(student: Student): List<SessionReceipt> {
        val cancelled = classes.cancelScheduledForUndoable(student.id)
        dao.upsert(student.copy(archived = true).toEntity())
        return cancelled
    }

    /**
     * Takes [archive] back: the student is active again, and each lesson it called off is restored while
     * it is unchanged since and its hour free. Returns how many could not be.
     */
    internal suspend fun unarchive(studentId: Long, cancelled: List<SessionReceipt>): Int {
        // The stored row, not the caller's copy, so an edit made in the window survives.
        dao.getById(studentId)?.let { dao.upsert(it.copy(archived = false)) }
        return classes.undoAll(cancelled).count { it != UndoResult.Restored }
    }

    /** Who is priced for [subjectId] — the candidates for a group bound to that subject. */
    fun observeIdsForSubject(subjectId: Long): Flow<List<Long>> = dao.observeStudentIdsForSubject(subjectId)

    // A student's subjects and their prices are kept in lock-step by EnrollmentRepository — the one
    // seam that links/unlinks a subject and sets/deletes its price together. Don't add link-only or
    // price-only mutators here; they let the displayed subjects and the bookable (priced) set diverge.
}
