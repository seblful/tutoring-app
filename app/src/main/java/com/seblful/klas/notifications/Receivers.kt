package com.seblful.klas.notifications

import android.content.BroadcastReceiver
import com.seblful.klas.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs [work] in [scope] with this broadcast held open (`goAsync`), finishing it however [work] ends.
 *
 * What every receiver of the app's does once it has decided the broadcast is its own, and each spelled it
 * out: `onReceive` runs on the main thread and the process may be killed the moment it returns, so the
 * wake-up is held until the database work is done — the `finally` is the point, since a broadcast never
 * finished is one the platform eventually kills the process over. [scope] is the application's, where work
 * that must complete lives. A failure is dropped, as each receiver dropped it: there is no screen here to
 * report it to.
 */
fun BroadcastReceiver.launchAsync(scope: CoroutineScope, work: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            runCatchingCancellable { work() }
        } finally {
            pending.finish()
        }
    }
}
