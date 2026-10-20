package com.seblful.klas

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
