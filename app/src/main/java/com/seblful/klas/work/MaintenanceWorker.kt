package com.seblful.klas.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seblful.klas.maintenance.Upkeep
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap

/**
 * Upkeep in the background. WorkManager's adapter onto [Upkeep] — what upkeep *does* is decided there,
 * where a JVM test can reach it. Its input says which [Upkeep.Trigger] it carries; none is the periodic tick.
 */
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val upkeep: Upkeep,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val trigger = inputData.getString(TRIGGER)?.let(Upkeep.Trigger::valueOf) ?: Upkeep.Trigger.PERIODIC
        upkeep.on(trigger)
        return Result.success()
    }

    companion object {
        /** The input key naming the [Upkeep.Trigger]. */
        const val TRIGGER = "trigger"
    }

    @AssistedFactory
    @WorkerKey(MaintenanceWorker::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : WorkerAssistedFactory {
        override fun create(appContext: Context, params: WorkerParameters): MaintenanceWorker
    }
}
