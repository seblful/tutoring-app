package com.seblful.klas

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.domain.model.AppLanguage
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

    /**
     * The base context is pinned to **a language the app actually has** — the one the phone asks for, or
     * English — before anything can read a string from it.
     *
     * Not the tutor's choice, which lives in the settings row and cannot be read here: Hilt has not run,
     * and the row is a suspending query. The two places that build text from the application context ask
     * for the chosen language themselves at the moment they use it (`Context.inAppLanguage()`), so this is
     * not where the setting is honoured. What it is for is the **plural rules**: those come from the
     * configuration's locale, not from the folder the strings were loaded from, so an unpinned Polish or
     * Arabic phone would apply its own quantity forms to English text. Resolving to a language the app is
     * written in makes the rules match the words in every case. See [inLanguage].
     */
    override fun attachBaseContext(base: Context) =
        super.attachBaseContext(base.inLanguage(AppLanguage.SYSTEM.localeFor(deviceLocales())))

    override fun onCreate() {
        super.onCreate()
        // Record crashes to a local file first, before any startup work can throw. The handler still
        // hands off to the platform default, so Google Play (Android Vitals) keeps reporting them.
        crashLogger.install()
        notifier.ensureChannels()
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
