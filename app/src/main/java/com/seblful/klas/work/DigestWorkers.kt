package com.seblful.klas.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seblful.klas.notifications.DueDigest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The morning digest, when it comes due. WorkManager's adapter onto [DueDigest] — what the digest *says*,
 * and whether it is worth saying at all, is decided there, where a JVM test can reach it.
 */
@HiltWorker
class AgendaWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val digest: DueDigest,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        digest.postAgenda()
        return Result.success()
    }
}

/** The weekly debt digest, when it comes due. As [AgendaWorker]: an adapter and nothing more. */
@HiltWorker
class DebtDigestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val digest: DueDigest,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        digest.postDebts()
        return Result.success()
    }
}
