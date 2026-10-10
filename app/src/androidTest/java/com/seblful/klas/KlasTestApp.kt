package com.seblful.klas

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.testing.CustomTestApplication

/**
 * What the instrumented tests run *as*, in place of Hilt's stock `HiltTestApplication`.
 *
 * [KlasApp] disables `WorkManagerInitializer` and provides WorkManager's configuration itself, so the
 * worker factory can be the Hilt one. Instrumentation swaps that class out — and the stock test
 * application is not a `Configuration.Provider`, so the first time the periodic maintenance job landed
 * in the test process, `SystemJobService.onCreate` threw «WorkManager is not initialized properly» and
 * took the process with it. The run then reported **zero tests** rather than a failure, and which run
 * that happened to was down to when the system chose to fire a periodic job: device tests died at
 * random, and the death looked like the test's fault.
 *
 * The factory here is the default one, not Hilt's: a `@HiltWorker` that does fire during a test cannot
 * be constructed and its work is marked failed, which is the right outcome — no test asserts anything
 * about workers, and the point is only that WorkManager can answer for itself without killing the run.
 *
 * A test that expects the app's *own* components to start (an activity, `BootReceiver`) still needs
 * `@HiltAndroidTest` + `HiltAndroidRule` so a Hilt component exists for them to be injected from. A
 * receiver the system wakes *before* the first test — `BOOT_COMPLETED`, at the start of the process — has
 * no rule to wait for, and is dropped by [HiltTestRunner].
 */
open class WorkAwareTestApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}

/** Generates `KlasTestApp_Application` — [WorkAwareTestApp] plus Hilt's test plumbing. */
@CustomTestApplication(WorkAwareTestApp::class)
interface KlasTestApp
