package com.seblful.klas.data.repository

import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.SubjectDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.NameOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepository @Inject constructor(
    private val dao: SubjectDao,
    private val classDao: ClassDao,
    private val groupDao: GroupDao,
) {
    fun observeAll(): Flow<List<Subject>> =
        dao.observeAll().map { list -> NameOrder.sorted(list.map { it.toDomain() }) { it.name } }

    suspend fun getById(id: Long): Subject? = dao.getById(id)?.toDomain()

    suspend fun upsert(subject: Subject): Long = dao.upsert(subject.toEntity())

    /**
     * Deletes [subject] only while nothing references it, and returns whether it did.
     *
     * Two things can, and each refuses for its own reason:
     *
     * - a **class**, because subjects are FK-cascade parents of `classes` (and `prices`), so deleting
     *   one with classes would silently destroy completed lessons — earned income and history — and
     *   leave payments stranded as false credit;
     * - a **group** that is taught it, because `student_groups.subjectId` is `ON DELETE RESTRICT`.
     *   That one has to be checked *here*: the constraint alone throws a `SQLiteConstraintException`
     *   from inside the `launch` the delete was called in, which reaches the tutor as a crash rather
     *   than as the "you can't do that yet" this returns. A brand-new group with no lessons of its own
     *   is enough to hit it, so it is not an exotic path.
     *
     * The caller should ask the user to clear the reference first.
     */
    suspend fun delete(subject: Subject): Boolean {
        if (classDao.countForSubject(subject.id) > 0) return false
        if (groupDao.countForSubject(subject.id) > 0) return false
        dao.delete(subject.toEntity())
        return true
    }
}
