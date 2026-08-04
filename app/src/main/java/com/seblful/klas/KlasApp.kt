package com.seblful.klas

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.work.Maintenance
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KlasApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    // The port, not the WorkManager adapter behind it: this was the one place in the app naming a `work/`
    // class, and it did so only because `ensurePeriodicMaintenance` was missing from the interface.
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var crashLogger: CrashLogger
    @Inject lateinit var maintenance: Maintenance

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // **The application's base context is never wrapped, and must not be.**
    //
    // This used to pin it to a language the app has — `super.attachBaseContext(base.inLanguage(…))` — for
    // the plural rules, since a quantity's form comes from the configuration's locale rather than from the
    // folder the string was loaded from. It cost every `BroadcastReceiver` in the app.
    //
    // `ActivityThread.handleReceiver` instantiates a receiver by casting the application's base context
    // straight to `ContextImpl`:
    //
    //     context = (ContextImpl) app.getBaseContext();
    //
    // `LocalizedContext` is a `ContextWrapper`, so that cast threw `ClassCastException` and the receiver
    // never came into existence — `ReminderReceiver` (so no reminder could ever be posted, whatever the
    // permission said), `ReminderActionReceiver` (so «Проведено» and «Перенести» did nothing) and
    // `BootReceiver` (so alarms stayed dead after a restart). The crash is in the framework's own
    // instantiation path, before `onReceive`, which is why nothing in this app could catch or report it and
    // why the failure looked exactly like a notification that was never scheduled.
    //
    // Nothing is lost by dropping it. The two surfaces that build text from the application context ask for
    // the language themselves at the moment they use it (`Context.inAppLanguage()` — `AndroidNotifier` and
    // the calendar mirror's event location), and every screen is handed one by `ProvideLocalizedResources`.
    // `MainActivity` still pins its own base context, which is safe: an activity is attached to a context
    // the framework built for it rather than having its wrapper cast back.

    override fun onCreate() {
        super.onCreate()
        // Record crashes to a local file first, before any startup work can throw. The handler still
        // hands off to the platform default, so Google Play (Android Vitals) keeps reporting them.
        crashLogger.install()
        notifier.ensureChannels()
        scheduler.ensurePeriodicMaintenance()

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
