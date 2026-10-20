package com.seblful.klas.data.billing

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices WHERE studentId = :studentId")
    fun observeForStudent(studentId: Long): Flow<List<PriceEntity>>

    @Query("SELECT * FROM prices WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun get(studentId: Long, subjectId: Long): PriceEntity?

    @Upsert
    suspend fun upsert(price: PriceEntity)

    @Query("DELETE FROM prices WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun delete(studentId: Long, subjectId: Long)
}

/**
 * The rate log. Ordered `atMillis DESC` so the newest change reads first — and because SQLite treats
 * NULL as the smallest value, the seeded "this rate predates the log" rows fall to the bottom, which
 * is where a change of unknown date belongs. `id DESC` breaks ties, so two changes recorded in the
 * same millisecond still come out in the order they were made.
 */
@Dao
interface RateHistoryDao {
    @Query("SELECT * FROM price_history WHERE studentId = :studentId ORDER BY atMillis DESC, id DESC")
    fun observeForStudent(studentId: Long): Flow<List<RateChangeEntity>>

    @Query(
        "SELECT * FROM price_history WHERE studentId = :studentId AND subjectId = :subjectId " +
            "ORDER BY atMillis DESC, id DESC",
    )
    fun observeForStudentSubject(studentId: Long, subjectId: Long): Flow<List<RateChangeEntity>>

    @Query("SELECT * FROM price_history WHERE groupId = :groupId ORDER BY atMillis DESC, id DESC")
    fun observeForGroup(groupId: Long): Flow<List<RateChangeEntity>>

    @Insert
    suspend fun insert(change: RateChangeEntity): Long

    @Query("DELETE FROM price_history WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun deleteForStudentSubject(studentId: Long, subjectId: Long)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY dateMillis DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY dateMillis DESC")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY dateMillis DESC")
    fun observeForStudent(studentId: Long): Flow<List<PaymentEntity>>

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    /**
     * Corrects an existing payment in place, keeping its id.
     *
     * Separate from [insert] rather than folded into it as a REPLACE: a payment is financial history,
     * and "insert, or silently overwrite whatever is already there" is the wrong default for a table
     * whose rows are money somebody handed over. An update is a deliberate correction and says so.
     */
    @Update
    suspend fun update(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)
}
