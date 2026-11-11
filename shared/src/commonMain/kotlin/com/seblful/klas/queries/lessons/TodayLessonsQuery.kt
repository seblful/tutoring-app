package com.seblful.klas.queries.lessons

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.schedule.DayBounds
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.days
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A whole lesson with the names both Today hosts display. */
data class NamedLesson(val session: ClassSession, val who: String, val subject: String)

/** One day's [NamedLesson]s, in time order. */
data class DayLessons(val day: LocalDate, val lessons: List<NamedLesson>)

/** Today's non-cancelled lessons, in time order, following names, local midnight and zone changes. */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
class TodayLessonsQuery @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
    private val clock: WallClock,
) {
    fun observe(): Flow<List<NamedLesson>> = observeDays(1).map { it.single().lessons }.distinctUntilChanged()

    /**
     * Today and the [count] - 1 days after it, each listed as [observe] lists today — for a host that draws
     * days ahead, such as iOS's widget timeline, which turns its pages while the app is not running.
     */
    fun observeDays(count: Int): Flow<List<DayLessons>> = clock.days()
        .flatMapLatest { today ->
            val days = List(count) { today.plus(it, DateTimeUnit.DAY) }
            val zone = clock.zone
            val bounds = DayBounds.of(days.first(), days.last().plus(1, DateTimeUnit.DAY), zone)
            combine(
                classes.observeBetween(bounds.start, bounds.end),
                students.observeAll(),
                subjects.observeAll(),
                groups.observeAll(),
            ) { spanClasses, studentList, subjectList, groupList ->
                val captions = Captions.of(studentList, subjectList, groupList)
                val named = SessionRules.sessions(spanClasses)
                    .filter { it.status != LessonStatus.CANCELLED }
                    .map { NamedLesson(it, captions.titleOf(it), captions.subjectOf(it)) }
                days.map { day ->
                    val dayBounds = DayBounds.of(day, zone)
                    DayLessons(day, named.filter { it.session.start in dayBounds.start..<dayBounds.end })
                }
            }
        }
        .distinctUntilChanged()
}
