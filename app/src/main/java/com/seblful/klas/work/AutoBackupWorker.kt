package com.seblful.klas.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seblful.klas.data.backup.AutoBackup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap

/**
 * The daily look at whether the weekly copy is due — WorkManager's adapter onto [AutoBackup]. A copy that
 * fails is not retried here: [AutoBackup] flags it for the Backup page, since a lost folder grant does not
 * come back by trying again.
 */
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val autoBackup: AutoBackup,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        autoBackup.runIfDue()
        return Result.success()
    }

    @AssistedFactory
    @WorkerKey(AutoBackupWorker::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : WorkerAssistedFactory {
        override fun create(appContext: Context, params: WorkerParameters): AutoBackupWorker
    }
}
