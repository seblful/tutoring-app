package com.seblful.klas.ui.schedule

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.seblful.klas.domain.guide.Tip
import com.seblful.klas.ui.common.ScreenTip

/**
 * Which lesson card carries the schedule's tip, and where its gestures report being made. Local to the schedule,
 * like [LocalLessonPicking], so its three views need not pass it through every list they build.
 *
 * @param reader whether a screen reader is on, which cannot swipe — asked once for the list, not by every card.
 */
@Immutable
internal class LessonTip(private val lessonId: Long?, val screen: ScreenTip, val reader: Boolean = false) {

    /** The tip [row]'s card shows, if it is the one carrying it. */
    fun on(row: ScheduleRow): Tip? = screen.tip.takeIf { lessonId != null && row.session.first.id == lessonId }

    companion object {
        val NONE = LessonTip(null, ScreenTip.NONE)
    }
}

internal val LocalLessonTip = staticCompositionLocalOf { LessonTip.NONE }
