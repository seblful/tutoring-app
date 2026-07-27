package com.seblful.klas.data.repository

import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.PriceDao
import com.seblful.klas.data.local.RateHistoryDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The rate log: what an hourly rate used to be, and when it changed.
 *
 * This exists as its own seam because **two different repositories set a rate** —
 * [EnrollmentRepository] owns a student's price for a subject, [GroupRepository] owns a group's own
 * rate — and both owe the same log. Deciding what counts as a change (and what an unchanged save
 * means) once here is what stops the two from recording it differently; neither of them writes to
 * `price_history` directly.
 *
 * Reads are per owner rather than "give me everything", because a rate history is only ever read
 * beside the rate it belongs to.
 */
@Singleton
class RateHistoryRepository @Inject constructor(
    private val dao: RateHistoryDao,
    /**
     * The two tables a rate actually lives in, so that [currentRate] can answer where a baseline comes
     * from. [RateOwner] already discriminates between them; before this, each caller resolved its own
     * half against its own DAO and the module only knew what it was told.
     */
    private val groupDao: GroupDao,
    private val priceDao: PriceDao,
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
     *
     * Which table that is, is this module's business rather than each caller's: [RateOwner] is the
     * discriminator, and it already lives here. Both callers used to resolve it themselves — one
     * against `GroupDao`, one against `PriceDao` — so "what was the rate before" had two
     * implementations for a log with one definition of what counts as a change.
     *
     * **Why this is a separate call and not folded into [record].** A rate is *clobbered* by the write
     * that changes it, and both callers log after that write: `GroupRepository` because a brand-new
     * group's id does not exist until its row is inserted, and the log's foreign key has to point at
     * something. Reading the baseline inside [record] would therefore read the value just written,
     * conclude nothing had changed, and silently drop the row — losing exactly the initial entry that
     * starts a new group's history. So the baseline is captured before the write and handed over
     * after it, and the two-step is the ordering, not an accident of the interface.
     */
    suspend fun currentRate(owner: RateOwner): Money? = when (owner) {
        is RateOwner.StudentSubject ->
            priceDao.get(owner.studentId, owner.subjectId)?.let { Money(it.amountMinor) }
        is RateOwner.GroupRate -> groupDao.getById(owner.groupId)?.let { Money(it.priceMinor) }
    }

    /**
     * Records that [owner]'s rate is now [to], having been [from], and reports whether anything was
     * written.
     *
     * **An unchanged rate logs nothing.** Both callers save a whole object — a group is re-saved when
     * only its name or roster changed, a student's subject is re-priced with the amount already in the
     * field — so logging every save would fill the history with "25 → 25" rows that hide the changes
     * the tutor is actually looking for. A null [from] is the one case that writes without a
     * difference to report: it means the rate is being set for the first time, which is the start of
     * the history rather than a change to it.
     */
    suspend fun record(owner: RateOwner, from: Money?, to: Money, at: Instant = Instant.now()): Boolean {
        if (from == to) return false
        dao.insert(RateChange(owner = owner, from = from, to = to, at = at).toEntity())
        return true
    }

    /**
     * Drops a (student, subject) rate's history, because the student no longer takes that subject.
     *
     * Called from [EnrollmentRepository.unenroll], which is only reachable for a subject the student
     * has **no classes** for — so nothing these rows describe ever billed anything. Keeping them would
     * make a later re-enrollment read as a continuation of a rate that had been withdrawn, with an
     * "initial" row sitting under earlier changes; clearing it means adding the subject back starts a
     * fresh, honest history.
     */
    suspend fun forget(owner: RateOwner.StudentSubject) {
        dao.deleteForStudentSubject(owner.studentId, owner.subjectId)
    }
}
