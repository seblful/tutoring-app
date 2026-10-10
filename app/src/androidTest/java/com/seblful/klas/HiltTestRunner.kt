package com.seblful.klas

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner

/**
 * Swaps in [KlasTestApp]'s generated application so Hilt can inject into instrumented tests — and so
 * WorkManager can answer for itself when one of its jobs lands in the test process. [WorkAwareTestApp]
 * is where the second half is argued; it is why this is not Hilt's stock `HiltTestApplication`.
 *
 * **And a receiver the system wakes before the first test has started is dropped, not crashed on.** Some
 * phones send the app a `BOOT_COMPLETED` of their own at the start of every process — Samsung's does,
 * from the `system` process, and the app cannot decline it. `BootReceiver` is injected, a test builds
 * its Hilt component only once its `HiltAndroidRule` runs, and the broadcast arrives first: the process
 * died before the first test and the run reported **zero tests** rather than a failure, on every start
 * until the phone happened to be slow enough. The rule on every test class covers a wake-up *during* a
 * test; this is the stretch before it. See [isReceiverWokenBeforeAnyTest].
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, KlasTestApp_Application::class.java.name, context)

    /**
     * The platform asks the instrumentation what to do with an exception from one of the app's own
     * components: answering `true` leaves the process running.
     *
     * **`goAsync()` first, to let go of the broadcast.** The platform finishes a failed broadcast *before* it
     * asks, and then finishes it again if the receiver still holds its pending result — which throws
     * «Broadcast already finished» and kills the process just the same. `goAsync()` is the public way to
     * hand that result over, and it is already finished, so nothing is left to finish twice.
     */
    override fun onException(obj: Any?, e: Throwable?): Boolean {
        if (obj is BroadcastReceiver && isReceiverWokenBeforeAnyTest(obj, e)) {
            obj.goAsync()
            Log.w(TAG, "Dropped ${obj.javaClass.simpleName}: woken before any test built a Hilt component")
            return true
        }
        return super.onException(obj, e)
    }

    private companion object {
        const val TAG = "HiltTestRunner"
    }
}

/**
 * Whether [error], thrown from [component], is a manifest receiver finding **no Hilt component yet** — the
 * one failure that is the run's timing and not the test's or the app's.
 *
 * Deliberately narrow: only a `BroadcastReceiver` and only Hilt's own "component was not created". An
 * activity that cannot start, or a receiver failing for any other reason, still takes the process down
 * and says why. If Hilt rewords the message this stops matching and the old crash comes back, which is
 * loud rather than a failure swallowed.
 */
internal fun isReceiverWokenBeforeAnyTest(component: Any?, error: Throwable?): Boolean =
    component is BroadcastReceiver &&
        error is IllegalStateException &&
        error.message?.contains(NO_COMPONENT_YET) == true

/** The start of the message Hilt's `TestApplicationComponentManager` throws without a `HiltAndroidRule`. */
private const val NO_COMPONENT_YET = "The component was not created"
