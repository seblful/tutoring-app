package com.seblful.klas.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * How a view model turns a query into the state a screen reads — the same three lines that used to end
 * twenty-six flows across thirteen view models.
 *
 * It is one policy stated three times per flow: share while somebody is subscribed, keep the upstream
 * query alive for a grace period after they stop, and start from a named initial value. Written out per
 * flow, the parts drift: the grace period is a bare `5_000` and would have to be changed in twenty-six
 * places, and whether a flow moves its work off the main thread was a `flowOn` that reads like
 * punctuation rather than like a decision.
 *
 * So there are **two** functions rather than one with an optional dispatcher. Which of them a flow uses
 * says whether it computes anything, and that is worth being able to see: [derive] for a flow that does
 * real work per emission, [hold] for one that only carries rows through.
 */

/**
 * How long the upstream query stays alive after the last subscriber goes.
 *
 * Long enough to survive a configuration change and a tab switch — re-subscribing inside it re-uses the
 * running query instead of re-running it, which for the reports screen means not recomputing every
 * class against every payment because the device was rotated. Short enough that a screen genuinely left
 * behind stops reading the database.
 */
private const val SUBSCRIPTION_GRACE_MILLIS = 5_000L

/**
 * State derived from [this], **computed off the main thread**.
 *
 * Use this wherever the flow's body does more than map a row: a `combine` of five queries, a sort, a
 * balance per student. `flowOn` governs only what is *upstream* of it, which is why it belongs here at
 * the end of the chain and not at the point the work happens.
 *
 * @param on the injected `@Computation` dispatcher, so tests keep one scheduler.
 * @param initial what the screen reads before the first emission — a real value, never a placeholder
 *   that would be indistinguishable from an empty database.
 */
fun <T> Flow<T>.derive(owner: ViewModel, on: CoroutineDispatcher, initial: T): StateFlow<T> =
    flowOn(on).hold(owner, initial)

/**
 * State that is just [this], shared — for a flow with nothing to compute, like a single row read back
 * or a permission re-check.
 *
 * Deliberately separate from [derive] rather than the same function with the dispatcher left out: a flow
 * that should have been moved off the main thread and was not looks identical to one that never needed
 * it, and this way the two are different words.
 */
fun <T> Flow<T>.hold(owner: ViewModel, initial: T): StateFlow<T> = stateIn(
    owner.viewModelScope,
    SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS),
    initial,
)
