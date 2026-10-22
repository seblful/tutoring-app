package com.seblful.klas.ui.students

import com.seblful.klas.queries.students.ClassRow
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.ui.people.LessonLine
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The student's card as of a minute: [details], with the lesson the tutor next sees them at and the few
 * listed under the lessons heading read off the same snapshot — see [StudentDetails.nextLesson] and
 * [StudentDetails.lessonPreview].
 */
data class StudentOverview(val details: StudentDetails, val next: NextLesson?, val lessons: List<ClassRow>) {

    companion object {
        /** How many lessons the card lists before «See all». */
        const val LESSONS_SHOWN = 3

        /** How many payments, and how many pieces of current homework. */
        const val PAYMENTS_SHOWN = 2
        const val HOMEWORK_SHOWN = 2

        fun of(details: StudentDetails, now: Instant, zone: TimeZone): StudentOverview = StudentOverview(
            details = details,
            next = details.nextLesson(now)?.let { row ->
                NextLesson(row, LessonLine.next(row.klass.start, now, zone), running = row.klass.start <= now)
            },
            lessons = details.lessonPreview(now, LESSONS_SHOWN),
        )
    }
}

/**
 * @param line when it is, said as the People list says it.
 * @param running whether it has begun — said as «In progress» rather than as a time already past.
 */
data class NextLesson(val row: ClassRow, val line: LessonLine.Next, val running: Boolean)
