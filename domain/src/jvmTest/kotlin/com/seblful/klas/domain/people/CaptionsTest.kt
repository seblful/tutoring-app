package com.seblful.klas.domain.people

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Reminders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CaptionsTest {

    private val start = Instant.parse("2026-06-20T15:00:00Z")

    private val captions = Captions.of(
        students = listOf(student(1, "Иван Петров"), student(2, "Аня Смирнова")),
        subjects = listOf(Subject(id = 10, name = "Алгебра")),
        groups = listOf(Group(id = 7, name = "11 «А»", subjectId = 10, price = Money.ofMajor(20))),
    )

    @Test
    fun `an individual lesson is titled by its student and names nobody else`() {
        val session = ClassSession.of(klass(id = 1, studentId = 1))

        assertEquals("Иван Петров", captions.titleOf(session))
        assertEquals("Алгебра", captions.subjectOf(session))
        assertEquals(emptyList<String>(), captions.membersOf(session), "its one student is already the title")
    }

    @Test
    fun `a group lesson is titled by its group and names its members in class order`() {
        val session = ClassSession(
            groupId = 7,
            classes = listOf(klass(id = 1, studentId = 2, groupId = 7), klass(id = 2, studentId = 1, groupId = 7)),
        )

        assertEquals("11 «А»", captions.titleOf(session))
        assertEquals(listOf("Аня Смирнова", "Иван Петров"), captions.membersOf(session))
    }

    @Test
    fun `a name it has never heard of comes back blank rather than missing`() {
        val stranger = ClassSession.of(klass(id = 1, studentId = 99, subjectId = 99))

        assertEquals("", captions.titleOf(stranger))
        assertEquals("", captions.subjectOf(stranger))
    }

    @Test
    fun `a group lesson whose group name is unknown or blank is titled by its first student`() {
        val unknownGroup = ClassSession(
            groupId = 99,
            classes = listOf(klass(id = 1, studentId = 2, groupId = 99), klass(id = 2, studentId = 1, groupId = 99)),
        )
        val blankGroup = Captions.of(
            students = listOf(student(2, "Аня Смирнова")),
            subjects = emptyList(),
            groups = listOf(Group(id = 7, name = " ", subjectId = 10, price = Money.ofMajor(20))),
        )

        assertEquals("Аня Смирнова", captions.titleOf(unknownGroup))
        assertEquals("Аня Смирнова", blankGroup.titleOf(ClassSession(7, listOf(klass(id = 1, studentId = 2, groupId = 7)))))
        assertEquals("Аня Смирнова", Reminders.announcedBy(groupName = " ", studentName = "Аня Смирнова"))
        assertEquals("11 «А»", Reminders.announcedBy(groupName = "11 «А»", studentName = "Аня Смирнова"))
        assertEquals("", Captions.title(groupName = null, studentName = null))
    }

    @Test
    fun `an individual class belongs to no group`() {
        assertNull(captions.groupOf(klass(id = 1, studentId = 1)))
    }

    @Test
    fun `a group class names the group it was taught in`() {
        assertEquals("11 «А»", captions.groupOf(klass(id = 1, studentId = 1, groupId = 7)))
    }

    @Test
    fun `a group whose name is unknown reads as no group at all`() {
        assertNull(captions.groupOf(klass(id = 1, studentId = 1, groupId = 99)))
    }

    private fun student(id: Long, name: String) = Student(id = id, name = name)

    private fun klass(
        id: Long,
        studentId: Long,
        subjectId: Long = 10,
        groupId: Long? = null,
    ) = TutoringClass(
        id = id,
        lessonId = if (groupId == null) id else 1_000L,
        studentId = studentId,
        subjectId = subjectId,
        start = start,
        durationMinutes = 60,
        price = Money.ofMajor(30),
        status = LessonStatus.SCHEDULED,
        groupId = groupId,
    )
}
