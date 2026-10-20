package com.seblful.klas.di

import com.seblful.klas.diagnostics.CrashLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A scope that lives as long as the process, for work that **must finish** once it has started.
 *
 * `viewModelScope` is cancelled the moment its screen goes away, which is fine for reading — a
 * query nobody is looking at is waste — but wrong for a write. Tapping «Сохранить» and immediately
 * going back would otherwise cancel the save partway: the lesson's rows are written in one
 * transaction ([com.seblful.klas.data.local.Transactor]), but the calendar mirror and the reminder
 * refresh that follow the commit are not, so a cancelled save could commit a lesson and skip the
 * work that makes it visible.
 *
 * A view model launches the *write* here and keeps the *navigation* in `viewModelScope`, so leaving
 * the screen abandons only the part that concerns the screen.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The dispatcher a view model does its **thinking** on: `Dispatchers.Default` in the app, a test
 * dispatcher in a test.
 *
 * Every screen's state is `combine(…repository flows…) { … }.stateIn(viewModelScope, …)`, and both of
 * those halves used to run on the main thread. Room's own `Flow` hands its rows over on Room's
 * executor, but `flowOn` only governs what is **upstream** of it — every operator after it runs in the
 * collector's context, and `stateIn(viewModelScope)` collects on `Dispatchers.Main.immediate`. So the
 * repositories' entity→domain mapping, `NameOrder.sorted` over every name, `SessionRules.sessions`,
 * `Taught.overall`, `Billing.balancesFor` and the whole of `Reports` were all running on the UI thread,
 * on every emission.
 *
 * `ReportsViewModel` is the case that makes it plain: five whole-table flows combined, walking every
 * class against every payment and every student — re-run on *each* lesson or payment written anywhere
 * in the app. It works at the scale a tutor starts with and stops working at the scale they arrive at,
 * which is the worst way for it to be wrong.
 *
 * So each of those chains carries `.flowOn(computation)` before its `stateIn`. One call covers the
 * whole upstream — the combine lambda *and* the repository mapping behind it — and `stateIn` still
 * publishes to the main thread, which is where state has to arrive.
 *
 * Injected rather than hard-coded so tests keep one scheduler: with a literal `Dispatchers.Default` the
 * state would settle on real threads that `advanceUntilIdle()` knows nothing about, and every view
 * model test would start racing its own subject.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Computation

@ContributesTo(AppScope::class)
@BindingContainer
object CoroutinesModule {

    /** See [Computation] for why this is injected rather than written at each `flowOn`. */
    @Provides
    @Computation
    fun provideComputationDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * `SupervisorJob` so one failed write cannot cancel the scope and take every later write with
     * it — each is independent, and the app has no notion of "all saving has stopped".
     */
    @Provides
    @SingleIn(AppScope::class)
    @ApplicationScope
    fun provideApplicationScope(crashLogger: CrashLogger): CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default +
                CoroutineExceptionHandler { _, throwable ->
                    runCatching { crashLogger.record(throwable) }
                },
        )
}
