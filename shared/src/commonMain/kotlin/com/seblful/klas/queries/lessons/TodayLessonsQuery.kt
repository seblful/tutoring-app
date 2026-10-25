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

/** A whole lesson with the names both Today hosts display. */
data class NamedLesson(val session: ClassSession, val who: String, val subject: String)

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
    fun observe(): Flow<List<NamedLesson>> = clock.days()
        .flatMapLatest { day ->
            val bounds = DayBounds.of(day, clock.zone)
            combine(
                classes.observeBetween(bounds.start, bounds.end),
                students.observeAll(),
                subjects.observeAll(),
                groups.observeAll(),
            ) { dayClasses, studentList, subjectList, groupList ->
                val captions = Captions.of(studentList, subjectList, groupList)
                SessionRules.sessions(dayClasses)
                    .filter { it.status != LessonStatus.CANCELLED }
                    .map { NamedLesson(it, captions.titleOf(it), captions.subjectOf(it)) }
            }
        }
        .distinctUntilChanged()
}
