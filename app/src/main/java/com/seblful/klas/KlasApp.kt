package com.seblful.klas

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.work.Maintenance
import com.seblful.klas.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KlasApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var crashLogger: CrashLogger
    @Inject lateinit var maintenance: Maintenance

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

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

        // Catch up **in process**, not by enqueueing a worker.
        //
        // This is what settles income: `Maintenance` auto-completes the lessons whose end time has
        // passed, and completing one is what bills it. Asking WorkManager to do that on the way in meant
        // the schedule and the reports the tutor was about to look at were as current as WorkManager
        // felt like being — and on an install the system has put in the restricted battery bucket, that
        // can be much later or not at all. Whereas the app *is* running, right here, and the work is
        // ordinary suspend code over the repositories.
        //
        // In [ApplicationScope] so it outlives this callback, and guarded so a failure at startup is
        // recorded rather than crashing the launch. The periodic worker stays as the safety net for
        // everything that happens while the app is closed.
        appScope.launch { runCatchingCancellable { maintenance.run() } }
    }
}
