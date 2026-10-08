package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.SubjectDao
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.local.upsertedId
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.SubjectNames
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.NameOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepository @Inject constructor(
    private val dao: SubjectDao,
    /** Both asked whether anything still references a subject — see their referenced-by guards. */
    private val classes: ClassRepository,
    private val groups: GroupRepository,
    /** Asked who is priced for a subject — see [delete]. */
    private val students: StudentDao,
    /** A subject's name is part of every mirrored event's title — see [CalendarMirror]. */
    private val calendar: CalendarMirror,
    /** Holds a name check and its write together — see [saveUnlessTaken]. */
    private val transactor: Transactor,
) {
    fun observeAll(): Flow<List<Subject>> =
        dao.observeAll().map { list -> NameOrder.sorted(list.map { it.toDomain() }) { it.name } }

    suspend fun getById(id: Long): Subject? = dao.getById(id)?.toDomain()

    /** Saves [subject] and refreshes the event titles carrying its name (see [StudentRepository.upsert]). */
    suspend fun upsert(subject: Subject): Long {
        val saved = upsertedId(dao.upsert(subject.toEntity()), subject.id)
        calendar.refreshForSubject(saved)
        return saved
    }

    /**
     * Saves [subject] unless another subject already has its name ([SubjectNames.sameAs]); false when
     * refused. Two «Математика» cannot be told apart anywhere they are offered.
     *
     * The check and the write are **one transaction**. The subjects screen and the first-run step each
     * used to read the list and then upsert, two statements apart, so a double tap on «Добавить» ran two
     * checks before either row existed and both went in. A write transaction is exclusive, so the second
     * tap's check now sees the first one's row.
     */
    suspend fun saveUnlessTaken(subject: Subject): Boolean {
        val saved = transactor.transaction {
            val all = dao.getAll().map { it.toDomain() }
            if (SubjectNames.sameAs(subject.name, all, exceptId = subject.id) != null) null
            else upsertedId(dao.upsert(subject.toEntity()), subject.id)
        } ?: return false
        calendar.refreshForSubject(saved)
        return true
    }

    /** Why a subject could not be deleted — the two things that can reference one. */
    sealed interface Delete {
        /** It was deleted. */
        data object Ok : Delete

        /** Refused: lessons reference it, and they are billed history. */
        data object HasClasses : Delete

        /**
         * Refused: a group is taught it. Distinct from [HasClasses] because the tutor has to be told
         * something different — "remove its lessons first" is no help at all when the blocker is a
         * brand-new group with no lessons.
         */
        data object TaughtToGroup : Delete

        /**
         * Refused: a student takes it, with no lesson in it yet. Their price for it cascades with the
         * subject, so the delete used to succeed and quietly take the subject off the student — for one
         * whose only subject it was, leaving a student nothing could be booked for.
         */
        data object TakenByStudents : Delete
    }

    /**
     * Deletes [subject] only while nothing references it, and says what stopped it if something did.
     *
     * Two things can, and each refuses for its own reason:
     *
     * - a **class**, because subjects are FK-cascade parents of `classes` (and `prices`), so deleting
     *   one with classes would silently destroy completed lessons — earned income and history — and
     *   leave payments stranded as false credit;
     * - a **student** who takes it, because `prices` and `student_subjects` cascade: the delete would
     *   un-enroll them without a word, and a student is booked only for what they take;
     * - a **group** that is taught it, because `student_groups.subjectId` is `ON DELETE RESTRICT`.
     *   That one has to be checked *here*: the constraint alone throws a `SQLiteConstraintException`
     *   from inside the `launch` the delete was called in, which reaches the tutor as a crash rather
     *   than as the "you can't do that yet" this returns. A brand-new group with no lessons of its own
     *   is enough to hit it, so it is not an exotic path.
     *
     * The caller should ask the user to clear the reference first, which is why the two are told apart.
     */
    suspend fun delete(subject: Subject): Delete {
        if (classes.anyForSubject(subject.id)) return Delete.HasClasses
        if (groups.anyForSubject(subject.id)) return Delete.TaughtToGroup
        if (students.getStudentIdsForSubject(subject.id).isNotEmpty()) return Delete.TakenByStudents
        dao.delete(subject.toEntity())
        return Delete.Ok
    }
}
