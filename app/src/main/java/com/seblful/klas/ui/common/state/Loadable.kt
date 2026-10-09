package com.seblful.klas.ui.common.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Whether a screen's state has arrived yet — the distinction every list screen was missing.
 *
 * State used to start at a real-but-empty value (`emptyList()`, `ScheduleContent.Day(emptyList())`,
 * `Reports.Report(period)`), and the screens branch on emptiness to choose between their content and
 * their empty state. So for the frame or two before Room's first emission, "nothing has loaded" and
 * "there is nothing" were the same value, and every screen drew the wrong one of the two: a cold open
 * of the schedule flashed «Нет занятий» *and its add-a-lesson button*, and «Люди» invited a tutor with
 * forty students to add their first. An app that says you have no students is not slow, it is wrong —
 * which is why this is a type and not a spinner someone remembered to add.
 *
 * `derive`'s KDoc asks for "a real value, never a placeholder that would be indistinguishable from an
 * empty database". [Loading] is that value: it is not a placeholder for a list, it is the answer that
 * there is no list yet.
 *
 * Deliberately not a third `Failure` case. A read here cannot fail in a way a screen could act on —
 * the database is local and its absence is a crash, not a state — and the app already reports failed
 * *writes* through `Messages`.
 */
sealed interface Loadable<out T> {

    /** No emission yet. Screens draw a skeleton shaped like the content — see `Skeleton.kt`. */
    data object Loading : Loadable<Nothing>

    /** The state, as the screen should draw it. Empty is a legitimate [value] and means empty. */
    data class Ready<out T>(val value: T) : Loadable<T>
}

/**
 * Marks every emission of [this] as arrived, so a flow can end in the app's usual
 * `.derive(this, computation, Loadable.Loading)`.
 *
 * A function rather than a `map` spelled per view model, for the same reason `derive` exists: it is the
 * kind of one-liner that gets pasted, and a flow that forgets it would compile — `Loadable.Loading`
 * would simply never be replaced, and the screen would show its skeleton forever.
 */
fun <T> Flow<T>.ready(): Flow<Loadable<T>> = map { Loadable.Ready(it) }

/**
 * [ready], for a question that is re-asked inside a `flatMapLatest` as the tutor pages — and which says
 * [Loadable.Loading] only once the new answer is **later than [grace]**.
 *
 * Saying it at once (`onStart { emit(Loading) }`) was correct and looked broken: a re-query answers in a
 * few frames, so every tap on another day blinked the list out to a skeleton and back, and the summary
 * line under the day's name went with it, jumping the list a line up and down. Inside the grace the state
 * keeps the previous answer, which is why a screen reading it must label what it draws by the answer's
 * own key (a day's content carries its date) and not by the question just asked.
 *
 * The grace is the bound on that: past it, the old answer is withdrawn, so a slow query still cannot
 * leave yesterday's lessons standing as though they were the answer.
 */
fun <T> Flow<T>.readyOrLoadingAfter(grace: Duration): Flow<Loadable<T>> = channelFlow {
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
