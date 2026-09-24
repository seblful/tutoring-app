package com.seblful.klas.domain

import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.HomeworkResult
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Homework set at one lesson and checked at the next — the whole of [Homework], on the JVM.
 *
 * The due lesson is derived, never stored, so every rule about *which* lesson checks what is here: the
 * track (group, or student and subject), skipping a cancelled hour, and what the tab lists once newer
 * homework has been set.
 */
class HomeworkTest {

    private val zone: ZoneId = ZoneId.of("Europe/Minsk")

    private var nextLesson = 1L
    private var nextClass = 1L

    /** One lesson — a class per member, sharing an id, an hour and the homework set at it. */
    private fun lesson(
        start: String,
        student: Long = ANYA,
        subject: Long = MATHS,
        group: Long? = null,
        members: List<Long> = listOf(student),
        status: LessonStatus = LessonStatus.COMPLETED,
        homework: String = "",
        results: Map<Long, HomeworkResult> = emptyMap(),
    ): List<TutoringClass> {
        val lessonId = nextLesson++
        return members.map { member ->
            TutoringClass(
                id = nextClass++,
                lessonId = lessonId,
                studentId = member,
                subjectId = subject,
                start = Instant.parse(start),
                durationMinutes = 60,
                status = status,
                groupId = group,
                homework = homework,
                homeworkResult = results[member],
            )
        }
    }

    private fun List<Homework.Assignment>.single(text: String) = single { it.text == text }

    // ---- which lesson checks it ----------------------------------------------------------------

    @Test
    fun `homework is due at the student's next lesson in that subject`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12, упр. 4–7")
        val next = lesson("2026-09-22T15:00:00Z")

        val assignment = Homework.assignments(set + next).single()

        assertEquals(next.first().lessonId, assignment.due?.lessonId)
    }

    /** Maths homework is checked at maths, not at Tuesday's physics in between. */
    @Test
    fun `another subject's lesson in between does not check it`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12")
        val physics = lesson("2026-09-16T15:00:00Z", subject = PHYSICS)
        val maths = lesson("2026-09-22T15:00:00Z")

        val assignment = Homework.assignments(set + physics + maths).single()

        assertEquals(maths.first().lessonId, assignment.due?.lessonId)
    }

    /**
     * A group's homework is the group's to bring: a member's own lesson the day after is somebody else's
     * track, however much the two share a student.
     */
    @Test
    fun `a group's homework is due at the group's next lesson`() {
        val set = lesson("2026-09-15T15:00:00Z", group = GROUP, members = listOf(ANYA, BORIS), homework = "Задачи 31–35")
        val anyaAlone = lesson("2026-09-16T15:00:00Z", student = ANYA)
        val groupNext = lesson("2026-09-22T15:00:00Z", group = GROUP, members = listOf(ANYA, BORIS))

        val assignment = Homework.assignments(set + anyaAlone + groupNext).single()

        assertEquals(groupNext.first().lessonId, assignment.due?.lessonId)
        assertEquals("every member's class is in it", 2, assignment.set.size)
    }

    /** Nothing was checked at an hour that did not happen, so the check moves on to the next one. */
    @Test
    fun `a cancelled lesson is skipped`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12")
        val cancelled = lesson("2026-09-22T15:00:00Z", status = LessonStatus.CANCELLED)
        val held = lesson("2026-09-29T15:00:00Z", status = LessonStatus.SCHEDULED)

        val assignment = Homework.assignments(set + cancelled + held).single()

        assertEquals(held.first().lessonId, assignment.due?.lessonId)
    }

    @Test
    fun `with no next lesson booked, it is due nowhere yet`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12")

        assertNull(Homework.assignments(set).single().due)
    }

    @Test
    fun `a cancelled lesson sets nothing`() {
        val set = lesson("2026-09-15T15:00:00Z", status = LessonStatus.CANCELLED, homework = "§12")

        assertTrue(Homework.assignments(set + lesson("2026-09-22T15:00:00Z")).isEmpty())
    }

    @Test
    fun `a lesson with no homework sets none`() {
        val quiet = lesson("2026-09-15T15:00:00Z")

        assertTrue(Homework.assignments(quiet + lesson("2026-09-22T15:00:00Z")).isEmpty())
    }

    // ---- what a lesson checks ------------------------------------------------------------------

    @Test
    fun `a lesson checks the homework set at the one before it`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12")
        val today = lesson("2026-09-22T15:00:00Z", status = LessonStatus.SCHEDULED)
        val session = GroupRules.sessions(today).single()

        assertEquals("§12", Homework.dueAt(session, set + today)?.text)
    }

    /** Homework two lessons back was due at the one in between, which held. */
    @Test
    fun `homework from two lessons back is not checked again`() {
        val old = lesson("2026-09-08T15:00:00Z", homework = "§11")
        val between = lesson("2026-09-15T15:00:00Z")
        val today = lesson("2026-09-22T15:00:00Z", status = LessonStatus.SCHEDULED)
        val session = GroupRules.sessions(today).single()

        assertNull(Homework.dueAt(session, old + between + today))
    }

    @Test
    fun `a cancelled lesson checks nothing`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12")
        val cancelled = lesson("2026-09-22T15:00:00Z", status = LessonStatus.CANCELLED)
        val session = GroupRules.sessions(cancelled).single()

        assertNull(Homework.dueAt(session, set + cancelled))
    }

    // ---- where it stands -----------------------------------------------------------------------

    @Test
    fun `its stage follows the due lesson's day, in the tutor's zone`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12")
        // 22:30 UTC on the 21st is 01:30 on the 22nd in Minsk — the day the tutor is living in.
        val next = lesson("2026-09-21T22:30:00Z", status = LessonStatus.SCHEDULED)
        val assignment = Homework.assignments(set + next).single()

        assertEquals(Homework.Stage.WAITING, assignment.stage(LocalDate.parse("2026-09-21"), zone))
        assertEquals(Homework.Stage.TODAY, assignment.stage(LocalDate.parse("2026-09-22"), zone))
        assertEquals(Homework.Stage.OVERDUE, assignment.stage(LocalDate.parse("2026-09-23"), zone))
    }

    @Test
    fun `once everybody is marked it is checked, whatever the day`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12", results = mapOf(ANYA to HomeworkResult.PARTIAL))
        val assignment = Homework.assignments(set).single()

        assertFalse(assignment.open)
        assertEquals(Homework.Stage.CHECKED, assignment.stage(LocalDate.parse("2026-09-30"), zone))
    }

    /** One member marked is not the group's homework checked — the others still have to be. */
    @Test
    fun `a group's homework stays open until every member is marked`() {
        val set = lesson(
            "2026-09-15T15:00:00Z", group = GROUP, members = listOf(ANYA, BORIS), homework = "Задачи",
            results = mapOf(ANYA to HomeworkResult.DONE),
        )

        assertTrue(Homework.assignments(set).single().open)
    }

    // ---- what the tab lists --------------------------------------------------------------------

    private val now = Instant.parse("2026-09-22T09:00:00Z")
    private val everyone = listOf(Student(ANYA, "Аня"), Student(BORIS, "Борис"))

    private fun worklist(classes: List<TutoringClass>, students: List<Student> = everyone, groups: List<Group> = emptyList()) =
        Homework.worklist(classes, now, zone, students, groups)

    @Test
    fun `the tab sorts open homework by the day of its lesson`() {
        val today = lesson("2026-09-15T15:00:00Z", homework = "today") +
            lesson("2026-09-22T15:00:00Z", status = LessonStatus.SCHEDULED)
        val overdue = lesson("2026-09-10T15:00:00Z", subject = PHYSICS, homework = "overdue") +
            lesson("2026-09-17T15:00:00Z", subject = PHYSICS)
        val waiting = lesson("2026-09-18T15:00:00Z", student = BORIS, homework = "waiting") +
            lesson("2026-09-25T15:00:00Z", student = BORIS, status = LessonStatus.SCHEDULED)
        val unbooked = lesson("2026-09-19T15:00:00Z", student = BORIS, subject = PHYSICS, homework = "unbooked")

        val list = worklist(today + overdue + waiting + unbooked)

        assertEquals(listOf("today"), list.today.map { it.text })
        assertEquals(listOf("overdue"), list.overdue.map { it.text })
        assertEquals(listOf("waiting"), list.waiting.map { it.text })
        assertEquals(listOf("unbooked"), list.unbooked.map { it.text })
        assertEquals(listOf("today", "overdue"), list.toCheck.map { it.text })
        assertEquals(listOf("waiting", "unbooked"), list.handedOut.map { it.text })
    }

    /** Newer homework on the same track replaces the older: the tab is about what to do next. */
    @Test
    fun `only the latest homework on a track is listed`() {
        val older = lesson("2026-09-08T15:00:00Z", homework = "§11")
        val newer = lesson("2026-09-15T15:00:00Z", homework = "§12")

        val list = worklist(older + newer)

        assertEquals(listOf("§12"), (list.toCheck + list.handedOut).map { it.text })
    }

    @Test
    fun `homework already checked is not listed`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12", results = mapOf(ANYA to HomeworkResult.DONE))

        val list = worklist(set + lesson("2026-09-22T15:00:00Z", status = LessonStatus.SCHEDULED))

        assertTrue(list.toCheck.isEmpty() && list.handedOut.isEmpty())
    }

    @Test
    fun `homework set longer ago than it is followed up for is not listed`() {
        val stale = lesson(now.minus(Homework.FOLLOWED_UP_FOR).minus(Duration.ofDays(1)).toString(), homework = "§1")

        assertTrue(worklist(stale).unbooked.isEmpty())
    }

    @Test
    fun `an archived student's homework is not listed`() {
        val set = lesson("2026-09-15T15:00:00Z", homework = "§12")

        val list = worklist(set, students = listOf(Student(ANYA, "Аня", archived = true)))

        assertTrue(list.unbooked.isEmpty())
    }

    @Test
    fun `an archived group's homework is not listed`() {
        val set = lesson("2026-09-15T15:00:00Z", group = GROUP, members = listOf(ANYA, BORIS), homework = "Задачи")

        val list = worklist(set, groups = listOf(Group(GROUP, "9 «Б»", archived = true)))

        assertTrue(list.unbooked.isEmpty())
    }

    @Test
    fun `an archived member leaves the group's homework, and the rest of it stays`() {
        val set = lesson("2026-09-15T15:00:00Z", group = GROUP, members = listOf(ANYA, BORIS), homework = "Задачи")

        val listed = worklist(set, students = listOf(Student(ANYA, "Аня"), Student(BORIS, "Борис", archived = true)))
            .unbooked.single("Задачи")

        assertEquals(listOf(ANYA), listed.set.classes.map { it.studentId })
    }

    // ---- a student's record --------------------------------------------------------------------

    @Test
    fun `a record counts each verdict, and what has none`() {
        val classes = lesson("2026-09-01T15:00:00Z", homework = "a", results = mapOf(ANYA to HomeworkResult.DONE)) +
            lesson("2026-09-08T15:00:00Z", homework = "b", results = mapOf(ANYA to HomeworkResult.DONE)) +
            lesson("2026-09-15T15:00:00Z", homework = "c", results = mapOf(ANYA to HomeworkResult.PARTIAL)) +
            lesson("2026-09-22T15:00:00Z", homework = "d", results = mapOf(ANYA to HomeworkResult.NOT_DONE)) +
            lesson("2026-09-29T15:00:00Z", homework = "e") +
            lesson("2026-10-06T15:00:00Z") +
            lesson("2026-10-13T15:00:00Z", status = LessonStatus.CANCELLED, homework = "f")

        val record = Homework.Record.of(classes)

        assertEquals(Homework.Record(done = 2, partial = 1, notDone = 1, unchecked = 1), record)
        assertEquals(4, record.checked)
    }

    @Test
    fun `a student's history is the homework set them, newest first`() {
        val classes = lesson("2026-09-01T15:00:00Z", homework = "a") +
            lesson("2026-09-08T15:00:00Z") +
            lesson("2026-09-15T15:00:00Z", homework = "b") +
            lesson("2026-09-22T15:00:00Z", status = LessonStatus.CANCELLED, homework = "c")

        assertEquals(listOf("b", "a"), Homework.setFor(classes).map { it.homework })
    }

    private companion object {
        const val ANYA = 1L
        const val BORIS = 2L
        const val MATHS = 10L
        const val PHYSICS = 11L
        const val GROUP = 20L
    }
}
