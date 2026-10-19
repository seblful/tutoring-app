package com.seblful.klas.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seblful.klas.notifications.DueDigest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap

/**
 * The morning digest, when it comes due. WorkManager's adapter onto [DueDigest] — what the digest *says*,
 * and whether it is worth saying at all, is decided there, where a JVM test can reach it.
 */
class AgendaWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val digest: DueDigest,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        digest.postAgenda()
        return Result.success()
    }

    @AssistedFactory
    @WorkerKey(AgendaWorker::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : WorkerAssistedFactory {
        override fun create(appContext: Context, params: WorkerParameters): AgendaWorker
    }
}

/**
 * The evening digest naming the lessons nobody marked, when it comes due. As [AgendaWorker]: an adapter
 * and nothing more.
 *
 * A separate worker rather than a parameter on [AgendaWorker], because WorkManager identifies unique work
 * by name and the two are enqueued under different names with different delays — a shared class would
 * still need two requests, and the branch deciding which digest a run is would live inside a
 * `CoroutineWorker`, which is precisely where this package refuses to put decisions.
 */
class UnfinishedDigestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val digest: DueDigest,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        digest.postUnfinished()
        return Result.success()
    }

    @AssistedFactory
    @WorkerKey(UnfinishedDigestWorker::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : WorkerAssistedFactory {
        override fun create(appContext: Context, params: WorkerParameters): UnfinishedDigestWorker
    }
}

/** The weekly debt digest, when it comes due. As [AgendaWorker]: an adapter and nothing more. */
class DebtDigestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val digest: DueDigest,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        digest.postDebts()
        return Result.success()
    }

    @AssistedFactory
    @WorkerKey(DebtDigestWorker::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : WorkerAssistedFactory {
        override fun create(appContext: Context, params: WorkerParameters): DebtDigestWorker
    }
}
