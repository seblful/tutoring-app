package com.seblful.klas.testutil

import kotlinx.coroutines.CompletableDeferred

class TransactionGate {
    private val entered = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()

    suspend fun awaitEntry() = entered.await()

    fun release() {
        released.complete(Unit)
    }

    internal suspend fun pause() {
        entered.complete(Unit)
        released.await()
    }
}
