package com.seblful.klas.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seblful.klas.notifications.DueReminder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fires one planned class reminder. WorkManager's adapter onto [DueReminder] — whether the reminder
 * is still owed and what it says are decided there, where a JVM test can reach them.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val due: DueReminder,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val classId = inputData.getLong(KEY_CLASS_ID, -1L)
        if (classId < 0) return Result.failure()
        due.post(classId)
        return Result.success()
    }

    companion object {
        const val KEY_CLASS_ID = "classId"
    }
}
