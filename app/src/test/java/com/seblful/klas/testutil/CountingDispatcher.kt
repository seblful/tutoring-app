package com.seblful.klas.testutil

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {

    var dispatches: Int = 0
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatches++
        delegate.dispatch(context, block)
    }
}
