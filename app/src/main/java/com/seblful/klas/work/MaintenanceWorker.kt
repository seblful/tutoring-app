package com.seblful.klas.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap

/**
 * Periodic upkeep. WorkManager's adapter onto [Maintenance] — what upkeep *does* is decided there,
 * where a JVM test can reach it.
 *
 * [Maintenance.runUnwatched] rather than `run`, because this is the pass with nobody in front of it: it
 * can auto-complete and therefore **bill** lessons at any hour with the app closed, and it used to do that
 * in silence. Which pass announces itself is decided in `Maintenance` for the same reason everything else
 * here is — a worker cannot be driven without a device, and this one moves the tutor's income.
 */
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val maintenance: Maintenance,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        maintenance.runUnwatched()
        return Result.success()
    }

    @AssistedFactory
    @WorkerKey(MaintenanceWorker::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : WorkerAssistedFactory {
        override fun create(appContext: Context, params: WorkerParameters): MaintenanceWorker
    }
}
