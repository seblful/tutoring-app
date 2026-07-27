package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Student
import com.seblful.klas.format.NameOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val dao: StudentDao,
    /**
     * Archiving a student has to settle their planned lessons, and every side effect of a class change
     * belongs to that class — so this reaches for it rather than restating any of it. The direction is
     * safe: `ClassRepository` depends on `StudentDao`, not on this.
     */
    private val classes: ClassRepository,
    /**
     * A student's name titles every event mirroring one of their lessons, and their email is what is
     * invited to it — so a rename or a corrected address makes those events wrong, and this is what
     * puts them right. See [CalendarMirror]: the obligation belongs to the module that does the reading.
     */
    private val calendar: CalendarMirror,
) {
    fun observeActive(): Flow<List<Student>> =
        dao.observeActive().map { list -> NameOrder.sorted(list.map { it.toDomain() }) { it.name } }

    /** Every student, archived ones last — they stay listed, out of the way of the active roster. */
    fun observeAll(): Flow<List<Student>> = dao.observeAll().map { list ->
        NameOrder.sortedArchivedLast(list.map { it.toDomain() }, archived = { it.archived }, name = { it.name })
    }

    fun observeById(id: Long): Flow<Student?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): Student? = dao.getById(id)?.toDomain()

    /**
     * Saves [student] and refreshes what their mirrored events say about them.
     *
     * The refresh is not incidental to a rename — it is what a rename *means* once the calendar is
     * mirroring. Best-effort and after the row, in that order: the local database is authoritative and
     * must never be held up by the calendar, so a failed refresh leaves a stale event to be corrected
     * rather than an unsaved student.
     */
    suspend fun upsert(student: Student): Long {
        val id = dao.upsert(student.toEntity())
        // @Upsert returns -1 when it updated rather than inserted — an edit, which is the case that
        // needs the refresh at all.
        val saved = if (id == -1L) student.id else id
        calendar.refreshForStudent(saved)
        return saved
    }

    /**
     * Archives or restores [student], and returns how many of their planned lessons archiving called
     * off (zero when restoring).
     *
     * **Archiving is more than a flag.** It retires the student — they leave every list, every picker
     * and, decisively, every report (`ReportsViewModel` counts active students only). A lesson of theirs
     * still marked scheduled does not retire with them: the maintenance pass auto-completes it, which
     * bills it, and the money then exists in the database while appearing in no total on any screen. The
     * debt the archive dialog warns about would keep growing where nobody could look at it. So the
     * planned lessons are called off first — see [ClassRepository.cancelScheduledFor], which also gives
     * the hours back, withdraws the calendar events and re-plans the reminders.
     *
     * Cancelling comes **before** the flag on purpose. If it fails, the student is still active with
     * their lessons called off — visible, correctable, and reported. The other order would leave an
     * archived student with live lessons, which is precisely the state being ruled out.
     *
     * Restoring does not un-cancel anything: a lesson that was called off is a fact, and the tutor can
     * put any of them back on the schedule themselves (`ClassRules` allows cancelled → scheduled).
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
