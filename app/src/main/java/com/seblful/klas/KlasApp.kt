package com.seblful.klas

import android.app.Application
import androidx.work.Configuration
import com.seblful.klas.di.AppGraph
import com.seblful.klas.di.KlasGraph
import dev.zacsweers.metro.createGraphFactory

class KlasApp : Application(), Configuration.Provider, AppGraph.Holder {

    /**
     * The process's one graph. Lazy, so the first thing to need it builds it — `onCreate`, or a receiver or
     * worker the system starts first.
     */
    override val graph: AppGraph by lazy { createGraphFactory<KlasGraph.Factory>().create(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(graph.workerFactory)
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
        graph.crashLogger.install()
        graph.notifier.ensureChannels()
        // The port, not the WorkManager adapter behind it: this was the one place in the app naming a `work/`
        // class, and it did so only because `ensurePeriodicMaintenance` was missing from the interface.
        graph.scheduler.ensurePeriodicMaintenance()
    }
}
