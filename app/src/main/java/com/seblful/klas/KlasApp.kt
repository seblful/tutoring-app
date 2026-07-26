package com.seblful.klas

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KlasApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var crashLogger: CrashLogger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // Before anything can read a string: workers and notifications are built from this context, and
    // they count things too. See [inRussian].
    override fun attachBaseContext(base: Context) = super.attachBaseContext(base.inRussian())

    override fun onCreate() {
        super.onCreate()
        // Record crashes to a local file first, before any startup work can throw. The handler still
        // hands off to the platform default, so Google Play (Android Vitals) keeps reporting them.
        crashLogger.install()
        notifier.ensureChannel()
        workScheduler.ensurePeriodicMaintenance()
        // Catch up immediately: auto-complete anything already past and schedule reminders.
        workScheduler.refreshNow()
    }
}
