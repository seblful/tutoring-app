package com.seblful.klas.data.settings

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Whether each of the first steps has happened at least once — see [GuideRepository.progress]. */
@Dao
interface GuideDao {
    @Query(
        "SELECT EXISTS(SELECT 1 FROM lessons) AS booked, " +
            "EXISTS(SELECT 1 FROM lessons WHERE status = 'COMPLETED') AS taught, " +
            "EXISTS(SELECT 1 FROM payments) AS paid, " +
            "EXISTS(SELECT 1 FROM homework) AS homework",
    )
    fun observeProgress(): Flow<ProgressRow>
}

data class ProgressRow(val booked: Boolean, val taught: Boolean, val paid: Boolean, val homework: Boolean)
