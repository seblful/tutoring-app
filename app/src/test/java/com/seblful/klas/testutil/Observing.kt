package com.seblful.klas.testutil

import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * The state a screen would actually be drawing — [Loadable.Ready]'s value, insisted upon.
 *
 * Screen state is wrapped in [Loadable] so a list that has not loaded is distinguishable from one that
 * is empty (see `ui/common/state/Loadable.kt`). Tests read the loaded value, and reading it through this
 * rather than through `.value` turns "the flow never emitted" from a silent empty list into a failure
 * naming the cause: before this, a test that forgot [observing] — or advanced the scheduler too early —
 * asserted happily against the placeholder, because the placeholder was an empty list of exactly the
 * shape the assertion expected.
 */
val <T> StateFlow<Loadable<T>>.current: T
    get() = when (val state = value) {
        Loadable.Loading -> error(
            "State is still Loadable.Loading. Nothing has been collected yet, so the view model's " +
                "`WhileSubscribed` flow has not started — is `observing(…)` missing, or does the " +
                "scheduler need advancing first?",
        )
        is Loadable.Ready -> state.value
    }
