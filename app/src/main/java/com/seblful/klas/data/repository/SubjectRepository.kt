package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.SubjectDao
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.local.upsertedId
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.people.NameOrder
import com.seblful.klas.domain.people.SubjectNames
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
class SubjectRepository @Inject constructor(
    private val dao: SubjectDao,

    private val classes: ClassRepository,
    private val groups: GroupRepository,

    private val students: StudentDao,

    private val calendar: CalendarMirror,

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

    /** eletes [subject] only while nothing references it, and says what stopped it if something did. */
    suspend fun delete(subject: Subject): Delete {
        if (classes.anyForSubject(subject.id)) return Delete.HasClasses
        if (groups.anyForSubject(subject.id)) return Delete.TaughtToGroup
        if (students.getStudentIdsForSubject(subject.id).isNotEmpty()) return Delete.TakenByStudents
        dao.delete(subject.toEntity())
        return Delete.Ok
    }
}
