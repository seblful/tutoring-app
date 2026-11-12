package com.seblful.klas.data.people

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.billing.RateHistoryRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.SessionReceipt
import com.seblful.klas.data.lessons.UndoResult
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.local.upsertedId
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.model.Group
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
 */
@SingleIn(AppScope::class)
class GroupRepository @Inject constructor(
    private val dao: GroupDao,

    private val classes: ClassRepository,
    private val studentDao: StudentDao,
    private val rateHistory: RateHistoryRepository,
    private val transactor: Transactor,

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

    fun observeMemberIds(groupId: Long): Flow<List<Long>> = dao.observeMemberIds(groupId)

    suspend fun getMemberIds(groupId: Long): List<Long> = dao.getMemberIds(groupId)

    /**
     * Whether any group is taught [subjectId] — the guard behind deleting a subject, asked here
     * because it is a question about groups. `student_groups.subjectId` is `ON DELETE RESTRICT`, and a
     * brand-new group with no lessons blocks the deletion just as firmly as one with a year of them,
     * which is why the caller has to tell the two refusals apart.
     */
    suspend fun anyForSubject(subjectId: Long): Boolean = dao.countForSubject(subjectId) > 0

    /** groupId → its member ids, for a screen that needs several groups' rosters at once. */
    fun observeRosters(): Flow<Map<Long, List<Long>>> =
        dao.observeAllMembers().map { refs ->
            refs.groupBy { it.groupId }.mapValues { (_, list) -> list.map { it.studentId } }
        }

    /**
     * Creates or updates [group] and makes its roster exactly the eligible part of [memberIds] —
     * links the new members and drops the ones no longer listed. Returns the group's id.
     */
    suspend fun save(group: Group, memberIds: Set<Long>): Long {
        // Both reads sit outside the transaction: neither depends on anything written inside it, and
        // the eligibility read in particular hits another table for a decision, not for a row to write.
        val previousName = dao.getById(group.id)?.name
        val wanted = eligible(group, memberIds)
        val id = transactor.transaction {
            // An edit answers -1 (see [upsertedId]); trusting it blindly would point every member
            // insert at group -1 and fail the group_members foreign key.
            val id = rateHistory.changing(
                owner = RateOwner.GroupRate(group.id),
                to = group.price,
                loggedAs = { written: Long -> RateOwner.GroupRate(written) },
            ) { upsertedId(dao.upsert(group.toEntity()), group.id) }
            val current = dao.getMemberIds(id).toSet()
            (wanted - current).forEach { dao.addMember(GroupMemberCrossRef(id, it)) }
            (current - wanted).forEach { dao.removeMember(id, it) }
            id
        }
        // After the commit, like every other calendar write: a group's name is what
        // makes n overlapping events read as one lesson, so a rename makes every mirrored event of
        // its lessons wrong — and is the only part of a save they show. See [CalendarMirror].
        if (previousName != null && previousName != group.name) calendar.refreshForGroup(id)
        return id
    }

    private suspend fun eligible(group: Group, memberIds: Set<Long>): Set<Long> {
        val subjectId = group.subjectId ?: return memberIds
        val priced = studentDao.getStudentIdsForSubject(subjectId).toSet()
        return memberIds intersect priced
    }

    /** Restores an archived [group]; the lessons archiving called off stay called off. */
    suspend fun restore(group: Group) {
        // The stored row, not the caller's copy, so an edit made since the screen read it survives.
        dao.getById(group.id)?.let { dao.upsert(it.copy(archived = false)) }
    }

    /**
     * Archives [group] undoably — as [StudentRepository.archive], for [unarchive] to take back. An archived group
     * can't be booked, so a planned lesson left standing would still remind, sync and auto-bill a group nobody can
     * see.
     */
    internal suspend fun archive(group: Group): List<SessionReceipt> =
        classes.cancelScheduledForGroupUndoable(group.id) {
            dao.getById(group.id)?.let { dao.upsert(it.copy(archived = true)) }
        }

    /** Takes [archive] back — as [StudentRepository.unarchive]; returns how many lessons stayed cancelled. */
    internal suspend fun unarchive(groupId: Long, cancelled: List<SessionReceipt>): Int {
        dao.getById(groupId)?.let { dao.upsert(it.copy(archived = false)) }
        return classes.undoAll(cancelled).count { it != UndoResult.Restored }
    }

    /**
     * Deletes [group] only when no class references it, and returns whether it did. `classes.groupId`
     * is `ON DELETE RESTRICT`, so a group with lessons can't be removed — the caller should offer to
     * archive it instead, which keeps the lessons readable.
     */
    suspend fun delete(group: Group): Boolean = transactor.transaction {
        if (classes.anyForGroup(group.id)) {
            false
        } else {
            dao.delete(group.toEntity())
            true
        }
    }
}
