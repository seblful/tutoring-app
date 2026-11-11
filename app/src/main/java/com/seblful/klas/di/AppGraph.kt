package com.seblful.klas.di

import android.content.Context
import androidx.work.WorkerFactory
import com.seblful.klas.KlasStore
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.format.AppLanguagePreferences
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.notifications.ReminderActionReceiver
import com.seblful.klas.notifications.ReminderReceiver
import com.seblful.klas.ui.AppSession
import com.seblful.klas.widget.MarkTaughtAction
import com.seblful.klas.widget.WidgetUpdates
import com.seblful.klas.work.BootReceiver
import com.seblful.klas.work.ClockChangeReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope

/**
 * What the app's own entry points need from its object graph — the pieces Android creates itself and so
 * cannot be handed their dependencies: the application, the activity's view models, WorkManager's workers,
 * the manifest's receivers and the home-screen widgets.
 */
interface AppGraph : ViewModelGraph {
    val appLanguages: AppLanguagePreferences
    val notifier: Notifier
    val scheduler: ReminderScheduler
    val crashLogger: CrashLogger
    val workerFactory: WorkerFactory
    val widgets: WidgetUpdates

    /** The app coming and going: its lock, and what the settings ask of the window and the launcher. */
    val session: AppSession

    /** For the application's startup work that need not hold up its first frame. */
    @ApplicationScope
    val applicationScope: CoroutineScope

    /** Fills a receiver's `@Inject` fields — called first thing in its `onReceive`. */
    fun inject(receiver: ReminderReceiver)
    fun inject(receiver: ReminderActionReceiver)
    fun inject(receiver: BootReceiver)
    fun inject(receiver: ClockChangeReceiver)
    fun inject(action: MarkTaughtAction)

    /** The application, which owns the one graph the process has. */
    interface Holder {
        val graph: AppGraph
    }
}

/**
 * The production graph: every binding contributed to [AppScope] anywhere in the app — `@ContributesBinding`
 * on an implementation, `@ContributesTo` on a binding container in `di/` — plus the application context.
 */
@DependencyGraph(AppScope::class)
interface KlasGraph : AppGraph, KlasStore {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides @ApplicationContext context: Context): KlasGraph
    }
}

/** The graph of the process this [Context] runs in, for a class Android instantiated itself. */
val Context.appGraph: AppGraph get() = (applicationContext as AppGraph.Holder).graph
