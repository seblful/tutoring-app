package com.seblful.klas.widget

import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.queries.lessons.TodayLessonsQuery
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** One row of the Today widget: a whole lesson, so a group lesson is one row, as on the schedule. */
data class TodayLesson(
    /** The lesson's first class — what opening it and marking it taught address. */
    val classId: Long,
    val start: Instant,
    val end: Instant,
    /** The group's name for a group lesson, the student's for an individual one ([Captions.titleOf]). */
    val who: String,
    val subject: String,
    val taught: Boolean,
    /** Whether the check can mark it taught: the transitions the schedule's own menu allows. */
    val canMarkTaught: Boolean,
) {
    /** Being taught at [now]: started, not over, not yet marked — the Day view's «On now». */
    fun isOnAt(now: Instant): Boolean = !taught && now >= start && now < end
}

/** Today's lessons for the widget, re-read when the lessons, the names on them, or the date change. */
@Inject
class TodayLessons(private val query: TodayLessonsQuery) {
    fun observe(): Flow<List<TodayLesson>> = query.observe()
        .map { lessons ->
            lessons.map { lesson ->
                val session = lesson.session
                TodayLesson(
                    classId = session.first.id,
                    start = session.start,
                    end = session.first.end,
                    who = lesson.who,
                    subject = lesson.subject,
                    taught = session.status == LessonStatus.COMPLETED,
                    canMarkTaught = LessonStatus.COMPLETED in SessionRules.sessionTransitions(session),
                )
            }
        }
        .distinctUntilChanged()

    companion object {
        /**
         * When [lessons] next change what the widget says after [now] — a lesson starting or ending, which
         * moves «On now» — or null when none is left today.
         */
        fun nextChange(lessons: List<TodayLesson>, now: Instant): Instant? =
            lessons.flatMap { listOf(it.start, it.end) }.filter { it > now }.minOrNull()
    }
}
