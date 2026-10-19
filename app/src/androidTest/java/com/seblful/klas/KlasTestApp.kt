package com.seblful.klas

import android.app.Application
import androidx.work.Configuration
import com.seblful.klas.di.AppGraph
import com.seblful.klas.di.KlasGraph
import dev.zacsweers.metro.createGraphFactory

/**
 * What the instrumented tests run *as*: the app's graph, without what `KlasApp.onCreate` does with it —
 * installing the crash logger, creating channels and scheduling the periodic upkeep are not the tests'
 * business, and must not happen to the device because a test ran.
 *
 * **The graph is there before any test asks for it.** Any of the app's own components can start in the
 * test process — `MainActivity` if the phone's owner opens the app mid-run, `BootReceiver` because some
 * phones (Samsung's) send `BOOT_COMPLETED` at the start of every process — and each takes its dependencies
 * from the application's graph. Under Hilt that graph existed only once a test's rule had built it, so such
 * a wake-up killed the whole run, which then reported zero tests; this one is built on first use.
 *
 * **And WorkManager can answer for itself.** `KlasApp` disables `WorkManagerInitializer` and provides the
 * configuration, so an application that is not a `Configuration.Provider` made `SystemJobService.onCreate`
 * throw «WorkManager is not initialized properly» the first time a periodic job landed in the test process.
 * The factory here is WorkManager's default rather than the graph's, so a worker that fires during a test
 * cannot be built and its work is marked failed: no test asserts anything about workers, and none should
 * have one bill a lesson behind its back.
 */
class KlasTestApp : Application(), Configuration.Provider, AppGraph.Holder {

    override val graph: AppGraph by lazy { createGraphFactory<KlasGraph.Factory>().create(this) }

    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}
