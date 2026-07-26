package com.seblful.klas.testutil

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A dispatcher that counts what is handed to it and passes it on — how a test can tell that a view
 * model's state really is derived off the main thread.
 *
 * `flowOn` is invisible from the outside: the state comes out the same either way, so a test that only
 * reads `state.value` passes whether the derivation ran on `Dispatchers.Main` or not. What *is*
 * observable is that the injected `@Computation` dispatcher was used at all — so wrap it in this, and a
 * `flowOn` deleted from the chain shows up as a count of zero.
 *
 * [delegate] is the test's own dispatcher, so the work still lands on the one scheduler that
 * `advanceUntilIdle()` drives.
 */
class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {

    var dispatches: Int = 0
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatches++
        delegate.dispatch(context, block)
    }
}
