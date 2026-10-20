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
import org.junit.Assert.assertEquals
import org.junit.Test
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

    @Test
    fun `a filter whose chips have gone hides nothing`() {
        val alone = row(1)
        val state = StudentDetails(classes = listOf(alone), lessonKinds = mapOf(LessonKind.INDIVIDUAL to 1))
        assertEquals(listOf(alone), state.lessonsShown(LessonFilter.GROUP))
    }
}
