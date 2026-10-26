package com.seblful.klas

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Runs [work] in [scope] with this broadcast held open (`goAsync`), finishing it however [work] ends. */
fun BroadcastReceiver.launchAsync(scope: CoroutineScope, work: suspend () -> Unit) {
    val pending = goAsync()
    launchHeldOpen(scope, { pending.finish() }, work)
}

/** [work] in [scope], then [finish] however it ends. A failure is left to the scope's exception handler. */
internal fun launchHeldOpen(scope: CoroutineScope, finish: () -> Unit, work: suspend () -> Unit) {
    scope.launch {
        try {
            work()
        } finally {
            finish()
        }
    }
}
