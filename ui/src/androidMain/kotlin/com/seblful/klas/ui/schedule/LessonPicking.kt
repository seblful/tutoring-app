package com.seblful.klas.ui.schedule

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How lesson cards answer while lessons can be picked ([LessonBatch]): a long-press picks one, and once any
 * is picked a tap picks too and the swipe rests, so a selection is never acted on by accident. Local to the
 * schedule, so its three views need not pass it through every list they build.
 */
@Immutable
internal class LessonPicking(val picked: Set<Long>, val toggle: (ScheduleRow) -> Unit) {
    val active: Boolean get() = picked.isNotEmpty()

    fun isPicked(row: ScheduleRow): Boolean = row.session.first.id in picked

    companion object {
        val NONE = LessonPicking(emptySet()) {}
    }
}

internal val LocalLessonPicking = staticCompositionLocalOf { LessonPicking.NONE }
