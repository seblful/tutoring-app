package com.seblful.klas.ui.homework

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkQuery
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.queries.homework.HomeworkState
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.valueOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class HomeworkViewModel @Inject constructor(
    homework: HomeworkRepository,
    private val query: HomeworkQuery,
    @param:ApplicationScope appScope: CoroutineScope,
    @Computation computation: CoroutineDispatcher,
    private val clock: WallClock,
) : ViewModel() {

    private val filter = MutableStateFlow<Assignee?>(null)

    /** Narrows the tab to [who] — a student (with their groups') or a group — or shows everybody with null. */
    fun setFilter(who: Assignee?) {
        filter.value = who
        batch.clear()
    }

    /**
     * The tab — [Loadable] because «Текущих заданий нет» is an answer, and the frame before the query
     * returns is not one.
     */
    val state: StateFlow<Loadable<HomeworkState>> =
        query.observe(filter)
            .ready()
            .derive(this, computation, Loadable.Loading)

    /**
     * Who the add dialog offers, **as of this moment** — whoever's lesson is nearest first, each with the
     * lesson homework set now would be attached to. Empty while there is nobody to set it for, which is the
     * tab's cue to say so instead of opening an empty dialog; **null before the tab has loaded**, which is
     * not the same answer and must not read as "add a student first".
     */
    fun choices(): List<AssigneeChoice>? = state.value.valueOrNull()?.choices(clock.now())

    /**
     * [choices] for correcting [row] — **always including whoever it is for now**, first if they are not
     * among the rest. Otherwise the dialog, which preselects the homework's own assignee from the list,
     * would fall back to somebody else and save the homework onto them.
     */
    suspend fun choicesFor(row: HomeworkRow): List<AssigneeChoice> = query.choicesFor(row)

    /** One-shot signals from an action that did not go through. */
    enum class Event {
        /** A write failed — a database error rather than a rule, said so the tap is not lost. */
        ACTION_FAILED,
    }

    val events = UiEvents<Event>()

    private val actions = HomeworkActions(homework, appScope, events, Event.ACTION_FAILED)

    /** Current homework picked by a long-press, among what the tab shows now — see [HomeworkBatch]. */
    val batch = HomeworkBatch(actions) { state.value.valueOrNull()?.lists?.current.orEmpty().map { it.homework } }

    fun give(assignee: Assignee, text: String) = actions.give(assignee, text)

    fun edit(item: Homework, assignee: Assignee, text: String) = actions.edit(item, assignee, text)

    fun setDone(item: Homework, done: Boolean) = actions.setDone(item, done)

    /** Undoes a swipe on [item], its done state back exactly as it was — see `HomeworkRepository.putBack`. */
    fun putBack(item: Homework) = actions.putBack(item)

    fun delete(item: Homework) = actions.delete(item)

    fun restore(item: Homework) = actions.restore(item)
}
