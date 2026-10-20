package com.seblful.klas.ui.common.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

private const val SUBSCRIPTION_GRACE_MILLIS = 5_000L

/**
 * State derived from [this], **computed off the main thread**.
 *
 * @param on the injected `@Computation` dispatcher, so tests keep one scheduler.
 * @param initial what the screen reads before the first emission — a real value, never a placeholder
 * that would be indistinguishable from an empty database.
 */
fun <T> Flow<T>.derive(owner: ViewModel, on: CoroutineDispatcher, initial: T): StateFlow<T> =
    flowOn(on).hold(owner, initial)

/**
 * State that is just [this], shared — for a flow with nothing to compute, like a single row read back
 * or a permission re-check.
 */
fun <T> Flow<T>.hold(owner: ViewModel, initial: T): StateFlow<T> = stateIn(
    owner.viewModelScope,
    SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS),
    initial,
)
