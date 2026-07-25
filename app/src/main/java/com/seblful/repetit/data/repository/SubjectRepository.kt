package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.ClassDao
import com.seblful.repetit.data.local.SubjectDao
import com.seblful.repetit.data.toDomain
import com.seblful.repetit.data.toEntity
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.format.NameOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepository @Inject constructor(
    private val dao: SubjectDao,
    private val classDao: ClassDao,
) {
    fun observeAll(): Flow<List<Subject>> =
        dao.observeAll().map { list -> NameOrder.sorted(list.map { it.toDomain() }) { it.name } }

    suspend fun getById(id: Long): Subject? = dao.getById(id)?.toDomain()

    suspend fun upsert(subject: Subject): Long = dao.upsert(subject.toEntity())

    /**
     * Deletes [subject] only if no class references it. Subjects are FK-cascade parents of
     * `classes` (and `prices`), so deleting one with classes would silently destroy completed
     * lessons — i.e. earned income and history — and leave payments stranded as false credit.
     * Returns false (deletes nothing) when the subject still has classes; the caller should ask
     * the user to remove those first.
     */
    suspend fun delete(subject: Subject): Boolean {
        if (classDao.countForSubject(subject.id) > 0) return false
        dao.delete(subject.toEntity())
        return true
    }
}
