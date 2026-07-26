package com.seblful.klas.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic upkeep. WorkManager's adapter onto [Maintenance] — what upkeep *does* is decided there,
 * where a JVM test can reach it.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val maintenance: Maintenance,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        maintenance.run()
        return Result.success()
    }
}
