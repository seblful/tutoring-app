package com.seblful.klas.notifications

import com.seblful.klas.domain.model.ClassSession
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Lessons marked taught **outside the app** — the widget's tick, a reminder's «Taught» — where no Undo can be
 * offered. Today lists those of the last [WINDOW], each with the guarded Undo every mark has. Kept in memory:
 * past the window the sheet's «Return to planned» is the way back, so a process restart loses nothing.
 */
@SingleIn(AppScope::class)
class RecentMarks @Inject constructor() {

    /** [session] as it was written taught, [at] that time. */
    data class Mark(val session: ClassSession, val at: Instant)

    private val _marks = MutableStateFlow<List<Mark>>(emptyList())

    val marks: StateFlow<List<Mark>> = _marks.asStateFlow()

    fun record(session: ClassSession, at: Instant) = _marks.update { marks ->
        marks.filterNot { it.session.lessonId == session.lessonId } + Mark(session, at)
    }

    fun forget(session: ClassSession) = _marks.update { marks ->
        marks.filterNot { it.session.lessonId == session.lessonId }
    }

    companion object {
        /** How long a mark made outside the app stays on Today with its Undo. */
        val WINDOW = 10.minutes
    }
}
