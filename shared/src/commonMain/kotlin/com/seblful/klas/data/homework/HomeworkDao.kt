package com.seblful.klas.data.homework

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Homework, one row per piece — see [HomeworkEntity].
 *
 * **Its two writes after the first are targeted `UPDATE`s**, not a whole row written back: homework is
 * swiped done from lists that can be an emission behind, and edited in a dialog opened from one. Writing
 * the row whole from either would put back whatever the other had changed in the meantime — an edit
 * un-doing a swipe, or a swipe restoring the old text.
 */
@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework")
    fun observeAll(): Flow<List<HomeworkEntity>>

    /** The homework not yet marked done — the only kind a calendar event describes. */
    @Query("SELECT * FROM homework WHERE doneAtMillis IS NULL")
    suspend fun getCurrent(): List<HomeworkEntity>

    @Query("SELECT * FROM homework WHERE id = :id")
    suspend fun getById(id: Long): HomeworkEntity?

    /** A student's own homework and their groups' — what their card lists. */
    @Query(
        "SELECT * FROM homework WHERE studentId = :studentId " +
            "OR groupId IN (SELECT groupId FROM group_members WHERE studentId = :studentId)",
    )
    fun observeForStudent(studentId: Long): Flow<List<HomeworkEntity>>

    /** A group's own homework — what its card lists. */
    @Query("SELECT * FROM homework WHERE groupId = :groupId")
    fun observeForGroup(groupId: Long): Flow<List<HomeworkEntity>>

    /** A new row — or, with its id, one being put back by an undo. */
    @Insert
    suspend fun insert(row: HomeworkEntity): Long

    @Query("UPDATE homework SET studentId = :studentId, groupId = :groupId, text = :text WHERE id = :id")
    suspend fun edit(id: Long, studentId: Long?, groupId: Long?, text: String)

    /** Marks it done at [doneAtMillis], or current again with null. */
    @Query("UPDATE homework SET doneAtMillis = :doneAtMillis WHERE id = :id")
    suspend fun setDone(id: Long, doneAtMillis: Long?)

    @Query("DELETE FROM homework WHERE id = :id")
    suspend fun deleteById(id: Long)
}
