package com.seblful.klas.ui.homework

import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.write
import kotlinx.coroutines.CoroutineScope

/**
 * The homework writes the «Задания» tab and the student and group cards all offer — give, correct, tick,
 * undo a swipe, delete, put back — each a guarded write into [appScope] that reports a failure as
 * [failure] on the view model's own [events].
 */
class HomeworkActions<E>(
    private val homework: HomeworkRepository,
    private val appScope: CoroutineScope,
    private val events: UiEvents<E>,
    private val failure: E,
) {

    fun give(assignee: Assignee, text: String) = write { homework.give(assignee, text) }

    fun edit(item: Homework, assignee: Assignee, text: String) = write { homework.edit(item, assignee, text) }

    fun setDone(item: Homework, done: Boolean) = write { homework.setDone(item, done) }

    /** Undoes a swipe on [item], its done state back exactly as it was — see `HomeworkRepository.putBack`. */
    fun putBack(item: Homework) = write { homework.putBack(item) }

    fun delete(item: Homework) = write { homework.delete(item) }

    fun restore(item: Homework) = write { homework.restore(item) }

    private fun write(block: suspend () -> Unit) = appScope.write(events, failure, block = block)
}
