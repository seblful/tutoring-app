package com.seblful.klas.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/**
 * Starts collecting [flows] for the rest of the test, standing in for the screen that would be
 * watching them.
 *
 * Every view model here shares its state with `stateIn(WhileSubscribed(5_000))`, which produces
 * nothing at all until somebody subscribes — so a test that only reads `.value` would see the initial
 * placeholder forever, however much data it seeded. Roughly sixty tests therefore opened with the
 * same line, and it is worth naming: `observing(vm.state)` says *why* the collector is there, where
 * `backgroundScope.launch { vm.state.collect {} }` only says what it does.
 *
 * Collects into [TestScope.backgroundScope] so the collector is torn down with the test rather than
 * leaving `runTest` waiting on a flow that never completes.
 *
 * Deliberately does **not** advance the scheduler: some tests subscribe and then arrange their data,
 * and the sequencing is theirs to decide. Tests that need the collector's `Job` back — the one
 * asserting the queries really do stop once nothing is watching — keep launching it by hand.
 */
fun TestScope.observing(vararg flows: Flow<*>) {
    flows.forEach { flow -> backgroundScope.launch { flow.collect {} } }
}
