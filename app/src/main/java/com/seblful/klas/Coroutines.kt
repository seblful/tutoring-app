package com.seblful.klas

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspending code: catches a genuine failure, but lets a **cancellation** through.
 *
 * `runCatching` catches `Throwable`, and coroutine cancellation *is* a `Throwable`
 * ([CancellationException]). Wrapping a suspend call in it therefore turns "this coroutine was
 * cancelled" into an ordinary failed [Result] — the caller carries on inside a coroutine that is
 * supposed to be dead, and, worse, a cancelled operation gets reported to the user as one that ran
 * and failed. An export the tutor navigated away from would say «Не удалось», and a restore cancelled
 * mid-flight would return as though it had finished.
 *
 * Every guarded suspend call in the app goes through this instead. There is no equivalent hazard for
 * non-suspending best-effort work (the calendar adapter's `ContentResolver` calls), which keeps
 * plain [runCatching].
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

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
