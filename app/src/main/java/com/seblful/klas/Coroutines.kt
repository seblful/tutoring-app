package com.seblful.klas

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
