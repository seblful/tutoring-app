package com.seblful.klas

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** runCatching] for suspending code: catches a genuine failure, but lets a **cancellation** through. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

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
