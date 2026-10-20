package com.seblful.klas.data.billing

import com.seblful.klas.data.people.GroupDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.time.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** he rate log: what an hourly rate used to be, and when it changed. */
@SingleIn(AppScope::class)
class RateHistoryRepository @Inject constructor(
    private val dao: RateHistoryDao,

    private val groupDao: GroupDao,
    private val priceDao: PriceDao,
    private val clock: WallClock,
) {
    /** Every recorded change to [owner]'s rate, newest first. */
    fun observeFor(owner: RateOwner): Flow<List<RateChange>> = when (owner) {
        is RateOwner.StudentSubject ->
            dao.observeForStudentSubject(owner.studentId, owner.subjectId)
        is RateOwner.GroupRate -> dao.observeForGroup(owner.groupId)
    }.map { list -> list.map { it.toDomain() } }

    /**
     * Every recorded change to any of one student's subject prices, newest first — the whole student
     * card's history in one query, since it shows all their subjects at once.
     */
    fun observeForStudent(studentId: Long): Flow<List<RateChange>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    /**
     * [owner]'s rate as currently stored, or null when it has none yet — the baseline a change is
     * measured against, and the [from] a caller should hand [record].
     */
    suspend fun currentRate(owner: RateOwner): Money? = when (owner) {
        is RateOwner.StudentSubject ->
            priceDao.get(owner.studentId, owner.subjectId)?.toDomain()?.amount
        is RateOwner.GroupRate -> groupDao.getById(owner.groupId)?.toDomain()?.price
    }

    /**
     * Records that [owner]'s rate is now [to], having been [from], and reports whether anything was
     * written.
     */
    suspend fun record(owner: RateOwner, from: Money?, to: Money, at: Instant = clock.now()): Boolean {
        if (from == to) return false
        dao.insert(RateChange(owner = owner, from = from, to = to, at = at).toEntity())
        return true
    }

    /** rops a (student, subject) rate's history, because the student no longer takes that subject. */
    suspend fun forget(owner: RateOwner.StudentSubject) {
        dao.deleteForStudentSubject(owner.studentId, owner.subjectId)
    }
}
