package com.seblful.klas

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** uns [work] in [scope] with this broadcast held open (`goAsync`), finishing it however [work] ends. */
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
