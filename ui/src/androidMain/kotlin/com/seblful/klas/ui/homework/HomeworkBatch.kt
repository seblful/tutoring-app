package com.seblful.klas.ui.homework

import androidx.compose.runtime.Composable
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.currentTexts
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Current homework picked by a long-press, moved to the archive together with **one** undo for the lot.
 *
 * @param current the current homework the tab shows — only what is still among it is moved.
 */
class HomeworkBatch(private val actions: HomeworkActions<*>, private val current: () -> List<Homework>) {

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())

    /** The picked pieces, by id. */
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    fun toggle(id: Long) = _selected.update { if (id in it) it - id else it + id }

    fun clear() {
        _selected.value = emptySet()
    }

    /** Moves the pick to the archive and closes it; returns the pieces as they were, empty if none was left. */
    fun markDone(): List<Homework> {
        val ids = _selected.value
        clear()
        val items = current().filter { it.id in ids }
        if (items.isNotEmpty()) actions.markDone(items)
        return items
    }

    /** Takes [markDone] back, each piece exactly as it was. */
    fun undo(items: List<Homework>) = actions.putBack(items)
}

/** The bar's action while homework is picked, saying what it did with one Undo. */
@Composable
internal fun MarkPickedDone(batch: HomeworkBatch, messages: Messages) {
    val resources = currentTexts()
    fun markDone() {
        val moved = batch.markDone()
        if (moved.isEmpty()) return
        val said = resources.plural(Plurals.homework_batch_done, moved.size, moved.size)
        messages.undoable(said) { batch.undo(moved) }
    }
    NamedIconButton(KlasIcons.Outlined.CheckCircle, stringResource(Strings.homework_batch_mark_done), ::markDone)
}
