package com.seblful.klas.ui.homework

import androidx.compose.runtime.Immutable
import com.seblful.klas.queries.homework.HomeworkRow

/**
 * How homework cards answer while pieces can be picked: a long-press picks one, and once any is picked a
 * tap picks too and the swipe rests, so a selection is never acted on by accident.
 *
 * @param enabled false where nothing can be picked — the archive, and the student and group cards.
 */
@Immutable
class HomeworkPicking(val picked: Set<Long>, val enabled: Boolean, val toggle: (HomeworkRow) -> Unit) {
    val active: Boolean get() = picked.isNotEmpty()

    fun isPicked(row: HomeworkRow): Boolean = row.homework.id in picked

    companion object {
        val NONE = HomeworkPicking(emptySet(), enabled = false) {}
    }
}
