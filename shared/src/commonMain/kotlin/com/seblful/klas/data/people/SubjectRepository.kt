package com.seblful.klas.data.people

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.lessons.ClassRepository
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

    sealed interface Save {
        data class Saved(val id: Long) : Save
        data class NameTaken(val existing: Subject) : Save
    }

    /** Resolves a typed name atomically, preserving an existing subject's spelling and identity. */
    suspend fun createOrReuse(name: String): Long {
        val subject = normalized(Subject(name = name))
        return transactor.transaction {
            matching(subject)?.id ?: upsertedId(dao.upsert(subject.toEntity()), subject.id)
        }
    }

    /** Saves or renames a subject without taking another's name; rename mirror work stays internal. */
    suspend fun save(subject: Subject): Save {
        val named = normalized(subject)
        val (result, renamed) = transactor.transaction {
            val taken = matching(named)
            if (taken != null) return@transaction Save.NameTaken(taken) to false
            val before = dao.getById(named.id)
            val id = upsertedId(dao.upsert(named.toEntity()), named.id)
            Save.Saved(id) to (before != null && before.name != named.name)
        }
        if (result is Save.Saved && renamed) calendar.refreshForSubject(result.id)
        return result
    }

    private fun normalized(subject: Subject): Subject = subject.copy(name = subject.name.trim()).also {
        require(it.name.isNotEmpty()) { "Enter a subject name." }
    }

    private suspend fun matching(subject: Subject): Subject? = SubjectNames.sameAs(
        subject.name,
        // Legacy imports can contain duplicate names; resolve consistently without rewriting them.
        dao.getAll().sortedBy { it.id }.map { it.toDomain() },
        exceptId = subject.id,
    )

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

    /** Deletes [subject] only while nothing references it, and says what stopped it if something did. */
    suspend fun delete(subject: Subject): Delete {
        if (classes.anyForSubject(subject.id)) return Delete.HasClasses
        if (groups.anyForSubject(subject.id)) return Delete.TaughtToGroup
        if (students.getStudentIdsForSubject(subject.id).isNotEmpty()) return Delete.TakenByStudents
        dao.delete(subject.toEntity())
        return Delete.Ok
    }
}
