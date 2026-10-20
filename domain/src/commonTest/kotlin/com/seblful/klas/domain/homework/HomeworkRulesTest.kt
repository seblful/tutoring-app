package com.seblful.klas.domain.homework

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HomeworkRulesTest {

    private val zone: TimeZone = TimeZone.of("Europe/Minsk")

    private val anya = Assignee.Student(1)
    private val boris = Assignee.Student(2)
    private val eleventh = Assignee.Group(10)

    private val mon: Instant = Instant.parse("2026-09-21T12:00:00Z")
    private val wed: Instant = Instant.parse("2026-09-23T12:00:00Z")
    private val fri: Instant = Instant.parse("2026-09-25T12:00:00Z")

    private var nextId = 1L

    private fun klass(
        studentId: Long,
        start: Instant,
        groupId: Long? = null,
        status: LessonStatus = LessonStatus.SCHEDULED,
        lessonId: Long = nextId,
    ) = TutoringClass(
        id = nextId++,
        lessonId = lessonId,
        studentId = studentId,
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        price = Money.ofMajor(30),
        status = status,
        groupId = groupId,
    )

    private fun homework(
        assignee: Assignee,
        givenAt: Instant,
        doneAt: Instant? = null,
        id: Long = nextId++,
    ) = Homework(id = id, assignee = assignee, text = "§ $id", givenAt = givenAt, doneAt = doneAt)

    @Test
    fun `homework is attached to its student's first lesson after it was given`() {
        val before = klass(1, mon)
        val next = klass(1, wed)
        val later = klass(1, fri)

        val attached = HomeworkRules.attachedTo(homework(anya, mon.plus(3600.seconds)), listOf(later, next, before))

        assertEquals(next, attached)
    }

    @Test
    fun `nobody else's lesson counts`() {
        val borisesSooner = klass(2, wed)
        val anyasOwn = klass(1, fri)

        assertEquals(anyasOwn, HomeworkRules.attachedTo(homework(anya, mon), listOf(borisesSooner, anyasOwn)))
    }

    @Test
    fun `a cancelled lesson is skipped`() {
        val cancelled = klass(1, wed, status = LessonStatus.CANCELLED)
        val next = klass(1, fri)

        assertEquals(next, HomeworkRules.attachedTo(homework(anya, mon), listOf(cancelled, next)))
    }

    @Test
    fun `a lesson under way when it was given is not the one it is for`() {
        val underWay = klass(1, mon)
        val next = klass(1, wed)

        assertEquals(next, HomeworkRules.attachedTo(homework(anya, mon.plus(1800.seconds)), listOf(underWay, next)))
    }

    @Test
    fun `a student's homework can be attached to a group lesson they are in`() {
        val groupLesson = klass(1, wed, groupId = 10)
        val ownLater = klass(1, fri)

        assertEquals(groupLesson, HomeworkRules.attachedTo(homework(anya, mon), listOf(groupLesson, ownLater)))
    }

    @Test
    fun `a group's homework is attached to the group's next lesson - not a member's own`() {
        val anyasOwn = klass(1, wed)
        val groupLesson = klass(1, fri, groupId = 10)

        assertEquals(groupLesson, HomeworkRules.attachedTo(homework(eleventh, mon), listOf(anyasOwn, groupLesson)))
    }

    @Test
    fun `with no lesson booked it is attached to none`() {
        assertNull(HomeworkRules.attachedTo(homework(anya, fri), listOf(klass(1, mon))))
    }

    @Test
    fun `due reads the lesson's day against today`() {
        val today = LocalDate(2026, 9, 23)
        fun due(lesson: TutoringClass?) = HomeworkRules.Current(homework(anya, mon), lesson).due(today, zone)

        assertEquals(HomeworkRules.Due.OVERDUE, due(klass(1, mon.plus(3600.seconds))))
        assertEquals(HomeworkRules.Due.TODAY, due(klass(1, wed)))
        assertEquals(HomeworkRules.Due.LATER, due(klass(1, fri)))
        assertEquals(HomeworkRules.Due.UNBOOKED, due(null))
    }

    /** Minsk is UTC+3, so a 22:30 UTC lesson is tomorrow's — the day is the tutor's, not the server's. */
    @Test
    fun `today is today in the tutor's zone`() {
        val lateUtc = klass(1, Instant.parse("2026-09-22T22:30:00Z"))

        val due = HomeworkRules.Current(homework(anya, mon), lateUtc).due(LocalDate(2026, 9, 23), zone)

        assertEquals(HomeworkRules.Due.TODAY, due)
    }

    // ---- The lists ---------------------------------------------------------------------------------

    @Test
    fun `current homework comes in the order its lessons do - the unbooked last and newest first`() {
        val classes = listOf(klass(1, wed), klass(2, fri))
        val forFriday = homework(boris, mon)
        val forWednesday = homework(anya, mon)
        val unbookedOld = homework(eleventh, mon)
        val unbookedNew = homework(eleventh, mon.plus(60.seconds))

        val current = HomeworkRules.lists(listOf(unbookedOld, forFriday, unbookedNew, forWednesday), classes).current

        assertEquals(listOf(forWednesday, forFriday, unbookedNew, unbookedOld), current.map { it.homework })
    }

    @Test
    fun `done homework is the archive - the most recently done first`() {
        val doneMonday = homework(anya, mon, doneAt = mon)
        val doneFriday = homework(anya, mon, doneAt = fri)
        val current = homework(anya, wed)

        val lists = HomeworkRules.lists(listOf(doneMonday, current, doneFriday), emptyList())

        assertEquals(listOf(doneFriday, doneMonday), lists.archive)
        assertEquals(listOf(current), lists.current.map { it.homework })
    }

    @Test
    fun `homework for an archived student or group is not the tab's`() {
        val students = listOf(Student(id = 1, name = "Аня"), Student(id = 2, name = "Борис", archived = true))
        val groups = listOf(Group(id = 10, name = "11 «А»", archived = true))
        val anyas = homework(anya, mon)

        val taught = HomeworkRules.taught(
            listOf(anyas, homework(boris, mon), homework(eleventh, mon), homework(Assignee.Student(99), mon)),
            students,
            groups,
        )

        assertEquals(listOf(anyas), taught)
    }

    @Test
    fun `a lesson's homework is whatever is attached to it - done or not`() {
        val earlier = klass(1, mon)
        val groupWed = listOf(klass(1, wed, groupId = 10, lessonId = 500), klass(2, wed, groupId = 10, lessonId = 500))
        val classes = listOf(earlier) + groupWed
        val old = homework(anya, mon.minus((86_400).seconds))
        val anyas = homework(anya, mon.plus(3600.seconds), doneAt = wed)
        val groups = homework(eleventh, mon)

        val attached = HomeworkRules.attachedToLesson(
            ClassSession(groupId = 10, classes = groupWed),
            listOf(anyas, old, groups),
            classes,
        )

        assertEquals(listOf(groups, anyas), attached)
    }

    // ---- The dialog's order ------------------------------------------------------------------------

    @Test
    fun `whoever's lesson is nearest now comes first - either side of it`() {
        val now = wed
        val classes = listOf(
            klass(1, mon), // Аня, two days back
            klass(2, wed.minus(3600.seconds)), // Борис, an hour ago
            klass(3, wed.plus(7200.seconds)), // Вера, in two hours
        )
        val vera = Assignee.Student(3)

        val order = HomeworkRules.byNearestLesson(listOf(anya, boris, vera), classes, now)

        assertEquals(listOf(boris, vera, anya), order)
    }

    @Test
    fun `nobody with a lesson falls behind - and the rest keep the order they came in`() {
        val vera = Assignee.Student(3)
        val classes = listOf(klass(2, fri), klass(1, wed, status = LessonStatus.CANCELLED))

        val order = HomeworkRules.byNearestLesson(listOf(anya, eleventh, boris, vera), classes, mon)

        assertEquals(listOf(boris, anya, eleventh, vera), order)
    }

    /** At 16:05 the lesson that ended five minutes ago is nearer than the one starting in twenty-five. */
    @Test
    fun `distance is to the lesson's hour - not its start`() {
        val now = Instant.parse("2026-09-23T16:05:00Z")
        val justTaught = klass(1, Instant.parse("2026-09-23T15:00:00Z")) // 15:00–16:00, over 5 minutes ago
        val bookedNext = klass(2, Instant.parse("2026-09-23T16:30:00Z")) // in 25 minutes

        assertEquals(
            listOf(anya, boris),
            HomeworkRules.byNearestLesson(listOf(boris, anya), listOf(bookedNext, justTaught), now),
        )
    }

    @Test
    fun `on a group lesson the group comes before its members`() {
        val lesson = listOf(klass(1, wed, groupId = 10, lessonId = 700), klass(2, wed, groupId = 10, lessonId = 700))

        val order = HomeworkRules.byNearestLesson(listOf(anya, boris, eleventh), lesson, wed.plus(600.seconds))

        assertEquals(listOf(eleventh, anya, boris), order)
    }

    @Test
    fun `narrowed to a student - the tab shows theirs and their groups'`() {
        val rosters = mapOf(10L to listOf(1L, 2L), 11L to listOf(2L))
        val anyas = homework(anya, mon)
        val groups = homework(eleventh, mon)
        val borises = homework(boris, mon)
        val otherGroup = homework(Assignee.Group(11), mon)

        val narrowed = HomeworkRules.narrowedTo(listOf(anyas, groups, borises, otherGroup), anya, rosters)

        assertEquals(listOf(anyas, groups), narrowed)
    }

    @Test
    fun `narrowed to a group - the tab shows the group's own and nobody's else`() {
        val rosters = mapOf(10L to listOf(1L))
        val groups = homework(eleventh, mon)

        assertEquals(listOf(groups), HomeworkRules.narrowedTo(listOf(homework(anya, mon), groups), eleventh, rosters))
    }

    @Test
    fun `the filter offers exactly those narrowing to something`() {
        val rosters = mapOf(10L to listOf(1L, 2L), 11L to listOf(3L))
        val carol = Assignee.Student(3)
        val dan = Assignee.Student(4)
        val work = listOf(homework(eleventh, mon), homework(dan, mon))
        val everyone = listOf(anya, boris, carol, dan, eleventh, Assignee.Group(11))

        assertEquals(
            everyone.filter { HomeworkRules.narrowedTo(work, it, rosters).isNotEmpty() },
            HomeworkRules.withHomework(work, everyone, rosters),
        )
        assertEquals(listOf(anya, boris, dan, eleventh), HomeworkRules.withHomework(work, everyone, rosters))
    }
}
