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

    // Keep the application base context unwrapped: Android casts it to ContextImpl when creating receivers.
    // A localized ContextWrapper crashed before onReceive, breaking reminders, notification actions, and
    // reboot recovery. Resolve the app language at each use site; MainActivity can wrap its own context.

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
