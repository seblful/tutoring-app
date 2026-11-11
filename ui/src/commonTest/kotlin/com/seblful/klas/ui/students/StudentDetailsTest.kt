package com.seblful.klas.ui.students

import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.queries.students.ClassRow
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.queries.students.SubjectPrice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class StudentDetailsTest {

    private val math = Subject(id = 1, name = "Математика")
    private val physics = Subject(id = 2, name = "Физика")

    private fun row(id: Long, groupId: Long? = null) = ClassRow(
        klass = TutoringClass(
            id = id, studentId = 1, subjectId = 1, start = Instant.fromEpochMilliseconds(0), durationMinutes = 60,
            status = LessonStatus.SCHEDULED, groupId = groupId,
        ),
        subjectName = "Математика",
        groupName = groupId?.let { "Группа" },
    )

    @Test
    fun `only subjects not yet priced for the student can be added`() {
        val state = StudentDetails(
            subjects = listOf(SubjectPrice(math, Money.ofMajor(30))),
            allSubjects = listOf(math, physics),
        )
        assertEquals(listOf(physics), state.addableSubjects)
    }

    @Test
    fun `the lesson filter narrows the list while the chips are on screen`() {
        val alone = row(1)
        val inGroup = row(2, groupId = 7)
        val state = StudentDetails(
            classes = listOf(alone, inGroup),
            lessonKinds = mapOf(LessonKind.INDIVIDUAL to 1, LessonKind.GROUP to 1),
        )
        assertEquals(listOf(alone, inGroup), state.lessonsShown(LessonFilter.ALL))
        assertEquals(listOf(alone), state.lessonsShown(LessonFilter.INDIVIDUAL))
        assertEquals(listOf(inGroup), state.lessonsShown(LessonFilter.GROUP))
    }

    private fun at(id: Long, startMinute: Int, status: LessonStatus = LessonStatus.SCHEDULED) = ClassRow(
        klass = TutoringClass(
            id = id, studentId = 1, subjectId = 1, start = Instant.fromEpochSeconds(startMinute * 60L),
            durationMinutes = 60, status = status,
        ),
        subjectName = "Математика",
    )

    private fun minute(m: Int) = Instant.fromEpochSeconds(m * 60L)

    @Test
    fun `the next lesson is the soonest not over — one under way included`() {
        val taught = at(1, 0, LessonStatus.COMPLETED)
        val running = at(2, 100)
        val later = at(3, 300)
        val state = StudentDetails(classes = listOf(later, running, taught))

        assertEquals(running, state.nextLesson(minute(130)))
        assertEquals(later, state.nextLesson(minute(160)))
        assertNull(state.nextLesson(minute(400)))
    }

    @Test
    fun `a cancelled lesson is never next`() {
        val cancelled = at(1, 100, LessonStatus.CANCELLED)
        val later = at(2, 300)
        assertEquals(later, StudentDetails(classes = listOf(later, cancelled)).nextLesson(minute(0)))
    }

    @Test
    fun `the preview shows the latest begun lessons — newest first — without the next one`() {
        val old = at(1, 0, LessonStatus.COMPLETED)
        val cancelled = at(2, 100, LessonStatus.CANCELLED)
        val recent = at(3, 200, LessonStatus.COMPLETED)
        val running = at(4, 300)
        val future = at(5, 500)
        val state = StudentDetails(classes = listOf(future, running, recent, cancelled, old))

        assertEquals(listOf(recent, cancelled), state.lessonPreview(minute(330), limit = 2))
    }

    @Test
    fun `with nothing begun the preview shows the bookings after the next — soonest first`() {
        val first = at(1, 100)
        val second = at(2, 200)
        val third = at(3, 300)
        val state = StudentDetails(classes = listOf(third, second, first))

        assertEquals(listOf(second, third), state.lessonPreview(minute(0), limit = 3))
        assertEquals(emptyList<ClassRow>(), StudentDetails().lessonPreview(minute(0), limit = 3))
    }

    @Test
    fun `a filter whose chips have gone hides nothing`() {
        val alone = row(1)
        val state = StudentDetails(classes = listOf(alone), lessonKinds = mapOf(LessonKind.INDIVIDUAL to 1))
        assertEquals(listOf(alone), state.lessonsShown(LessonFilter.GROUP))
    }
}
