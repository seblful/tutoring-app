package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentDao
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

    /** aves [student] and refreshes what their mirrored events say about them. */
    suspend fun upsert(student: Student): Long {
        // An edit answers -1 (see [upsertedId]) — and an edit is exactly the case that needs the
        // refresh at all.
        val saved = upsertedId(dao.upsert(student.toEntity()), student.id)
        calendar.refreshForStudent(saved)
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

    /** Who is priced for [subjectId] — the candidates for a group bound to that subject. */
    fun observeIdsForSubject(subjectId: Long): Flow<List<Long>> = dao.observeStudentIdsForSubject(subjectId)

    // A student's subjects and their prices are kept in lock-step by EnrollmentRepository — the one
    // seam that links/unlinks a subject and sets/deletes its price together. Don't add link-only or
    // price-only mutators here; they let the displayed subjects and the bookable (priced) set diverge.
}
