package com.seblful.klas.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.MapKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.reflect.KClass

/**
 * Makes a worker of one class: WorkManager supplies the context and the parameters, the graph the rest.
 * Each worker declares one as its nested `@AssistedFactory`, keyed by [WorkerKey].
 */
interface WorkerAssistedFactory {
    fun create(appContext: Context, params: WorkerParameters): ListenableWorker
}

/** Files a [WorkerAssistedFactory] under the worker class it makes. */
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class WorkerKey(val value: KClass<out ListenableWorker>)

/**
 * WorkManager's way into the graph: it asks for a worker by class name and gets one with its dependencies
 * injected. `null` for a class nobody contributed, which hands the request to WorkManager's own reflective
 * factory — and that one fails a worker that takes anything beyond the two parameters, as it should.
 */
@Inject
@ContributesBinding(AppScope::class, binding = binding<WorkerFactory>())
@SingleIn(AppScope::class)
class AppWorkerFactory(
    private val factories: Map<KClass<out ListenableWorker>, () -> WorkerAssistedFactory>,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = factories.entries
        .firstOrNull { (type, _) -> type.java.name == workerClassName }
        ?.value?.invoke()
        ?.create(appContext, workerParameters)
}
