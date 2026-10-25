package com.seblful.klas.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.work.WorkerFactory
import com.seblful.klas.KlasStore
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.format.AppLanguagePreferences
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.notifications.ReminderActionReceiver
import com.seblful.klas.notifications.ReminderReceiver
import com.seblful.klas.widget.MarkTaughtAction
import com.seblful.klas.widget.WidgetUpdates
import com.seblful.klas.work.BootReceiver
import com.seblful.klas.work.ClockChangeReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

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

/** The application's `Context`, as opposed to an activity's — the only one the graph holds. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationContext

/** Builds every screen's view model from the graph; `metroViewModel()` finds it in the composition. */
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AppViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualFactory>, () -> ManualFactory>,
) : MetroViewModelFactory()

private typealias ManualFactory = ManualViewModelAssistedFactory
