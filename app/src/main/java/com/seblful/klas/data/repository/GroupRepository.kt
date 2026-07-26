package com.seblful.klas.data.repository

import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.format.NameOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class GroupRepository @Inject constructor(
    private val dao: GroupDao,
    private val classDao: ClassDao,
    private val studentDao: StudentDao,
    private val rateHistory: RateHistoryRepository,
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

    /** studentId → the groups it belongs to, for showing a student's groups. */
    fun observeMembershipsByStudent(): Flow<Map<Long, List<Long>>> =
        dao.observeAllMembers().map { refs ->
            refs.groupBy { it.studentId }.mapValues { (_, list) -> list.map { it.groupId } }
        }

    /**
     * Creates or updates [group] and makes its roster exactly [memberIds] — links the new members
     * and drops the ones no longer listed. Returns the group's id (assigned on insert).
     *
     * Dropping a member only stops *future* bookings from including them; their past classes keep
     * their group id, so a group lesson someone left still reads as the group lesson it was.
     */
    suspend fun save(group: Group, memberIds: Set<Long>): Long {
        // Read before writing: the stored rate is what the new one is a change *from*.
        val previousRate = dao.getById(group.id)?.let { Money(it.priceMinor) }
        // Room's @Upsert returns the new rowid when it inserts, but -1 when it updated an existing
        // row instead. Trusting it blindly on an edit made every member insert reference group -1
        // and fail the group_members foreign key, so fall back to the id we were handed.
        val rowId = dao.upsert(group.toEntity())
        val id = if (rowId == -1L) group.id else rowId
        // Logged after the upsert, so the row the log's foreign key points at already exists — and
        // only when the amount moved, since a rename or a roster edit comes through here too
        // (RateHistoryRepository.record drops the no-op).
        rateHistory.record(RateOwner.GroupRate(id), from = previousRate, to = group.price)
        val wanted = eligible(group, memberIds)
        val current = dao.getMemberIds(id).toSet()
        (wanted - current).forEach { dao.addMember(GroupMemberCrossRef(id, it)) }
        (current - wanted).forEach { dao.removeMember(id, it) }
        return id
    }

    /**
     * Narrows [memberIds] to the students a subject-bound group may actually contain.
     *
     * A group with a subject only teaches that subject, so a member who is not priced for it could
     * never be billed for one of its lessons — such a member is dropped here rather than kept as a
     * roster entry that silently fails at booking time. A group with no subject (everything made
     * before v6) is left exactly as asked.
     *
     * This is enforced in the repository, not just the form, because [save] is the only writer of
     * membership: a caller that skipped the UI could otherwise seed an unbillable roster.
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
        if (classDao.countForGroup(group.id) > 0) return false
        dao.delete(group.toEntity())
        return true
    }
}
