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
     * Writes [owner]'s rate as [to] through [write] and logs the change, inside the caller's transaction. The
     * rate before is read here, ahead of [write], so no change can be measured against its own write; nothing
     * is logged when the amount did not move, as a rename or a roster edit saves the rate unchanged.
     * [loggedAs] names the owner [write] produced — a new group's id exists only once its row does, and the
     * log's foreign key points at that row.
     */
    internal suspend fun <T> changing(
        owner: RateOwner,
        to: Money,
        loggedAs: (T) -> RateOwner = { owner },
        write: suspend () -> T,
    ): T {
        val from = currentRate(owner)
        val written = write()
        if (from != to) {
            dao.insert(RateChange(owner = loggedAs(written), from = from, to = to, at = clock.now()).toEntity())
        }
        return written
    }

    /** [owner]'s rate as stored, or null when it has none yet — which is what starts a history. */
    private suspend fun currentRate(owner: RateOwner): Money? = when (owner) {
        is RateOwner.StudentSubject ->
            priceDao.get(owner.studentId, owner.subjectId)?.toDomain()?.amount
        is RateOwner.GroupRate -> groupDao.getById(owner.groupId)?.toDomain()?.price
    }

    /** Drops a (student, subject) rate's history, because the student no longer takes that subject. */
    internal suspend fun forget(owner: RateOwner.StudentSubject) {
        dao.deleteForStudentSubject(owner.studentId, owner.subjectId)
    }
}
