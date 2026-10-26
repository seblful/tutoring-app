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
 * Process scope for writes that must outlive the screen that started them.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Dispatcher for upstream repository mapping and state combination. `flowOn` keeps that work off the
 * main thread; injection lets tests use their controlled dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Computation

/**
 * Dispatcher for blocking file, content-provider and calendar calls, injected for the reason [Computation] is.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Io

@ContributesTo(AppScope::class)
@BindingContainer
object CoroutinesModule {

    /** See [Computation] for why this is injected rather than written at each `flowOn`. */
    @Provides
    @Computation
    fun provideComputationDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Io
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

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
