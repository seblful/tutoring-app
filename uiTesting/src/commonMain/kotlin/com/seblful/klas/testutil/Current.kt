package com.seblful.klas.testutil

import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.flow.StateFlow

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
