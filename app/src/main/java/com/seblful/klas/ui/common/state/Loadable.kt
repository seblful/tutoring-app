package com.seblful.klas.ui.common.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** hether a screen's state has arrived yet — the distinction every list screen was missing. */
sealed interface Loadable<out T> {

    /** No emission yet. Screens draw a skeleton shaped like the content — see `Skeleton.kt`. */
    data object Loading : Loadable<Nothing>

    /** The state, as the screen should draw it. Empty is a legitimate [value] and means empty. */
    data class Ready<out T>(val value: T) : Loadable<T>
}

/**
 * Marks every emission of [this] as arrived, so a flow can end in the app's usual
 * `.derive(this, computation, Loadable.Loading)`.
 */
fun <T> Flow<T>.ready(): Flow<Loadable<T>> = map { Loadable.Ready(it) }

/**
 * [ready], for a question that is re-asked inside a `flatMapLatest` as the tutor pages — and which says
 * [Loadable.Loading] only once the new answer is **later than [grace]**.
 */
fun <T> Flow<T>.readyOrLoadingAfter(grace: Duration = PAGE_TURN_GRACE): Flow<Loadable<T>> = channelFlow {
    val late = launch {
        delay(grace)
        send(Loadable.Loading)
    }
    collect {
        late.cancel()
        send(Loadable.Ready(it))
    }
}

/** The state if it has arrived, `null` while it has not — for the few reads that want no branch. */
fun <T> Loadable<T>.valueOrNull(): T? = (this as? Loadable.Ready)?.value

/**
 * The same loaded-ness, about part of the state — for a screen that hands one slice of it to a
 * component, as the schedule hands the selected day's rows to its list while keeping the week's
 * sections for itself.
 */
fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> = when (this) {
    Loadable.Loading -> Loadable.Loading
    is Loadable.Ready -> Loadable.Ready(transform(value))
}

/**
 * How long the previous page's answer may stand while the next is read — see [readyOrLoadingAfter]. Long
 * enough to cover a re-query on a slow phone, short enough that a stuck one is never mistaken for an answer.
 */
val PAGE_TURN_GRACE: Duration = 250.milliseconds
