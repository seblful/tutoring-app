package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.ClassDao
import com.seblful.repetit.data.local.GroupDao
import com.seblful.repetit.data.local.GroupMemberCrossRef
import com.seblful.repetit.data.toDomain
import com.seblful.repetit.data.toEntity
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.format.NameOrder
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
 */
@Singleton
class GroupRepository @Inject constructor(
    private val dao: GroupDao,
    private val classDao: ClassDao,
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
        val id = dao.upsert(group.toEntity())
        val current = dao.getMemberIds(id).toSet()
        (memberIds - current).forEach { dao.addMember(GroupMemberCrossRef(id, it)) }
        (current - memberIds).forEach { dao.removeMember(id, it) }
        return id
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
