package com.seblful.klas.data.repository

import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.local.upsertedId
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.people.NameOrder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Groups and their rosters. This is the **only writer of group membership**: a group is always saved
 * together with the exact set of members it should have ([save]), so a caller can't create a group
 * and then forget to attach anyone, or leave a stale member behind after an edit.
 *
 * A group owns no money — its members' classes do — so re-staffing or renaming one never touches
 * history. What history does constrain is deletion: [delete] refuses a group that has classes,
 * because those classes are billed lessons that must keep naming the group they were taught in.
 * Retiring such a group is [setArchived], mirroring students.
 *
 * A group *does* own a **rate**, and [save] is the only place it changes, so this is also where a
 * group's rate history is logged (see [RateHistoryRepository]).
 */
@SingleIn(AppScope::class)
class GroupRepository @Inject constructor(
    private val dao: GroupDao,
    /** Asked whether a group still has lessons — see `ClassRepository`'s referenced-by guards. */
    private val classes: ClassRepository,
    private val studentDao: StudentDao,
    private val rateHistory: RateHistoryRepository,
    private val transactor: Transactor,
    /** A group's name titles every event of its lessons — see [CalendarMirror]. */
    private val calendar: CalendarMirror,
) {
    fun observeActive(): Flow<List<Group>> =
        dao.observeActive().map { list -> NameOrder.sorted(list.map { it.toDomain() }) { it.name } }

    /** Every group, archived ones last — mirroring students. */
    fun observeAll(): Flow<List<Group>> = dao.observeAll().map { list ->
        NameOrder.sortedArchivedLast(list.map { it.toDomain() }, archived = { it.archived }, name = { it.name })
    }

    fun observeById(id: Long): Flow<Group?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): Group? = dao.getById(id)?.toDomain()

    suspend fun getAll(): List<Group> = dao.getAll().map { it.toDomain() }

    fun observeMemberIds(groupId: Long): Flow<List<Long>> = dao.observeMemberIds(groupId)

    suspend fun getMemberIds(groupId: Long): List<Long> = dao.getMemberIds(groupId)

    /**
     * Whether any group is taught [subjectId] — the guard behind deleting a subject, asked here
     * because it is a question about groups. `student_groups.subjectId` is `ON DELETE RESTRICT`, and a
     * brand-new group with no lessons blocks the deletion just as firmly as one with a year of them,
     * which is why the caller has to tell the two refusals apart.
     */
    suspend fun anyForSubject(subjectId: Long): Boolean = dao.countForSubject(subjectId) > 0

    /**
     * groupId → its member ids, for a screen that needs several groups' rosters at once.
     *
     * Keyed by **group**, which is how every caller reads it. It used to be keyed by student, so the
     * groups list inverted it back a group at a time — scanning every student's memberships once per
     * group, on every emission, to find the entry it had just been given the other way round.
     */
    fun observeRosters(): Flow<Map<Long, List<Long>>> =
        dao.observeAllMembers().map { refs ->
            refs.groupBy { it.groupId }.mapValues { (_, list) -> list.map { it.studentId } }
        }

    /**
     * What [save] did: the group's [id] (assigned on insert), and the members it **left out** —
     * asked for, but not priced for the group's subject.
     *
     * [dropped] comes back as data because the fact is the save's own. The form used to predict it
     * with an intersection of its own over a different query, which is two modules holding one rule
     * — the report and the write could disagree, and only the write was real.
     */
    data class Saved(val id: Long, val dropped: Set<Long>)

    /**
     * Creates or updates [group] and makes its roster exactly the eligible part of [memberIds] —
     * links the new members and drops the ones no longer listed. Returns [Saved]: the group's id,
     * and who was left out.
     *
     * A group with a subject only teaches that subject, so a member not priced for it could never be
     * billed for one of its lessons — [save] keeps only the priced ones. That is enforced here, not
     * just offered in the form, because this is the only writer of membership: a caller that skipped
     * the UI could otherwise seed a roster no lesson could bill. It is also not a hypothetical way to
     * lose someone: un-enrolling a student from the subject one-to-one is allowed (while the group
     * has taught no lesson), and the next save of the group — **a rename is enough**, every save
     * re-intersects — is what drops them. [Saved.dropped] is what keeps that from happening silently.
     *
     * Dropping a member only stops *future* bookings from including them; their past classes keep
     * their group id, so a group lesson someone left still reads as the group lesson it was.
     *
     * All of it is **one transaction** — the group row, its rate log entry, and every roster link
     * added or removed. A group is saved *with* a roster, which is the whole reason this is the only
     * writer of membership; a save that stopped between the two would leave the half-roster nobody
     * asked for, and it is the members' classes that get billed.
     */
    suspend fun save(group: Group, memberIds: Set<Long>): Saved {
        // Both reads sit outside the transaction: neither depends on anything written inside it, and
        // the eligibility read in particular hits another table for a decision, not for a row to write.
        val previousRate = rateHistory.currentRate(RateOwner.GroupRate(group.id))
        val wanted = eligible(group, memberIds)
        val id = transactor.transaction {
            // An edit answers -1 (see [upsertedId]); trusting it blindly once pointed every member
            // insert at group -1 and failed the group_members foreign key.
            val id = upsertedId(dao.upsert(group.toEntity()), group.id)
            // Logged after the upsert, so the row the log's foreign key points at already exists — and
            // only when the amount moved, since a rename or a roster edit comes through here too
            // (RateHistoryRepository.record drops the no-op).
            rateHistory.record(RateOwner.GroupRate(id), from = previousRate, to = group.price)
            val current = dao.getMemberIds(id).toSet()
            (wanted - current).forEach { dao.addMember(GroupMemberCrossRef(id, it)) }
            (current - wanted).forEach { dao.removeMember(id, it) }
            id
        }
        // Phase 3, outside the transaction like every other calendar write: a group's name is what
        // makes n overlapping events read as one lesson, so a rename makes every mirrored event of
        // its lessons wrong. See [CalendarMirror].
        calendar.refreshForGroup(id)
        return Saved(id, dropped = memberIds - wanted)
    }

    /**
     * Narrows [memberIds] to the students a subject-bound group may actually contain — see [save].
     * A group with no subject (one restored from an older backup, or not yet given one) is left
     * exactly as asked.
     */
    private suspend fun eligible(group: Group, memberIds: Set<Long>): Set<Long> {
        val subjectId = group.subjectId ?: return memberIds
        val priced = studentDao.getStudentIdsForSubject(subjectId).toSet()
        return memberIds intersect priced
    }

    suspend fun setArchived(group: Group, archived: Boolean) {
        dao.upsert(group.copy(archived = archived).toEntity())
    }

    /**
     * Deletes [group] only when no class references it, and returns whether it did. `classes.groupId`
     * is `ON DELETE RESTRICT`, so a group with lessons can't be removed — the caller should offer to
     * archive it instead, which keeps the lessons readable.
     */
    suspend fun delete(group: Group): Boolean {
        if (classes.anyForGroup(group.id)) return false
        dao.delete(group.toEntity())
        return true
    }
}
