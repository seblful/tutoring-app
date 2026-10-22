package com.seblful.klas

import android.app.Application
import androidx.work.Configuration
import com.seblful.klas.di.AppGraph
import com.seblful.klas.di.KlasGraph
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.launch

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
        // Off the main thread: WorkManager initializes on first use, and every cold start runs this —
        // including one a receiver or worker caused. Posting a notification creates the channels itself.
        graph.applicationScope.launch {
            graph.notifier.ensureChannels()
            graph.scheduler.ensurePeriodicMaintenance()
        }
        graph.applicationScope.launch { graph.widgets.keepCurrent() }
    }
}
