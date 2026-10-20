package com.seblful.klas.testutil

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    /**
     * Stands in for the injected `@ApplicationScope` — the scope a view model hands its **writes**
     * to so they outlive the screen. Built on this rule's [dispatcher], so it shares the test's one
     * scheduler and `advanceUntilIdle()` drives a write launched into it just like a
     * `viewModelScope` one.
     *
     * Deliberately not a child of the test's own scope: the point of the production scope is that
     * cancelling the view model does not cancel the write, and a test asserting that needs a scope
     * with the same independence.
     */
    val appScope: CoroutineScope = CoroutineScope(dispatcher)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
