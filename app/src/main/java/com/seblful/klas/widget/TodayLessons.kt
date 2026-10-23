package com.seblful.klas.widget

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.schedule.DayBounds
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.days
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
class TodayLessons(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
    private val clock: WallClock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<List<TodayLesson>> =
        clock.days()
            .flatMapLatest { day ->
                val bounds = DayBounds.of(day, clock.zone)
                combine(
                    classes.observeBetween(bounds.start, bounds.end),
                    students.observeAll(),
                    subjects.observeAll(),
                    groups.observeAll(),
                ) { dayClasses, studentList, subjectList, groupList ->
                    rows(dayClasses, Captions.of(studentList, subjectList, groupList))
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

        /**
         * One row per lesson, in time order. Cancelled lessons are left out: the widget is for what is
         * still to teach today and what has been taught.
         */
        fun rows(dayClasses: List<TutoringClass>, captions: Captions): List<TodayLesson> =
            SessionRules.sessions(dayClasses)
                .filter { it.status != LessonStatus.CANCELLED }
                .map { session ->
                    TodayLesson(
                        classId = session.first.id,
                        start = session.start,
                        end = session.first.end,
                        who = captions.titleOf(session),
                        subject = captions.subjectOf(session),
                        taught = session.status == LessonStatus.COMPLETED,
                        canMarkTaught = LessonStatus.COMPLETED in SessionRules.sessionTransitions(session),
                    )
                }
    }
}
