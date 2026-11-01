package com.seblful.klas.queries.homework

import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeworkQueryTest {
    private val env = TestEnv()
    private val query = env.store().homeworkQuery
    private val repo = env.homeworkRepo
    private val fixtures = env.fixtures
    private val todayAt17 = Instant.parse("2026-06-01T14:00:00Z")
    private val tomorrow = Instant.parse("2026-06-02T12:00:00Z")

    @AfterTest
    fun close() = env.close()

    private fun CoroutineScope.watch(filter: MutableStateFlow<Assignee?>): StateFlow<HomeworkState?> =
        query.observe(filter).stateIn(this, SharingStarted.Eagerly, null)

    @Test
    fun `reassigning old homework previews the actual historical destination lesson`() = runTest {
        val subject = fixtures.subject()
        val first = fixtures.pricedStudent("First", subject)
        val second = fixtures.pricedStudent("Second", subject)
        val givenAt = Instant.parse("2026-03-01T12:00:00Z")
        val destination = Instant.parse("2026-03-05T12:00:00Z")
        fixtures.lesson(subject, listOf(second), destination)
        fixtures.lesson(subject, listOf(second), tomorrow)
        fixtures.homework(Assignee.Student(first), at = givenAt, done = true)
        val filter = MutableStateFlow<Assignee?>(null)
        val state = backgroundScope.watch(filter)
        runCurrent()
        val row = state.value!!.lists.archive.single()

        val preview = query.choicesFor(row).single { it.assignee == Assignee.Student(second) }
        assertEquals(destination, preview.nextLessonAt)
        repo.edit(row.homework, preview.assignee, row.text)
        runCurrent()
        val saved = env.homeworkRepo.observeAll().first().single()
        assertEquals(destination, HomeworkRules.attachedTo(saved, env.classRepo.getAll())?.start)
    }

    @Test
    fun `current homework is captioned by whom it is for and says when its lesson is`() =
        runTest {
            val maths = fixtures.subject()
            val anya = fixtures.pricedStudent("Аня", maths)
            fixtures.lesson(maths, listOf(anya), todayAt17)
            fixtures.homework(Assignee.Student(anya), text = "§ 12")
            val filter = MutableStateFlow<Assignee?>(null)
            val state = backgroundScope.watch(filter)
            runCurrent()

            val row = state.value!!.lists.current.single()

            assertEquals("§ 12", row.text)
            assertEquals("Аня", row.title)
            assertEquals(todayAt17, row.lessonAt)
            assertEquals(HomeworkRules.Due.TODAY, row.due)
        }

    @Test
    fun `a group's homework is titled by the group`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        fixtures.homework(Assignee.Group(group))
        val filter = MutableStateFlow<Assignee?>(null)
        val state = backgroundScope.watch(filter)
        runCurrent()

        val row = state.value!!.lists.current.single()

        assertEquals("9 «Б»", row.title)
        assertTrue(row.isGroup)
        assertEquals(HomeworkRules.Due.UNBOOKED, row.due)
    }

    @Test
    fun `an archived student's homework leaves the tab`() = runTest {
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        fixtures.homework(Assignee.Student(anya), text = "Ани")
        fixtures.homework(Assignee.Student(boris), text = "Бориса", done = true)
        env.studentRepo.setArchived(env.studentRepo.getById(boris)!!, archived = true)
        val filter = MutableStateFlow<Assignee?>(null)
        val state = backgroundScope.watch(filter)
        runCurrent()

        assertEquals(listOf("Ани"), state.value!!.lists.current.map { it.text })
        assertTrue(state.value!!.lists.archive.isEmpty())
    }

    @Test
    fun `the dialog puts the nearest lesson's student first with the lesson it will go to`() =
        runTest {
            val maths = fixtures.subject()
            val anya = fixtures.pricedStudent("Аня", maths)
            val boris = fixtures.pricedStudent("Борис", maths)
            val borisNext = Instant.parse("2026-06-08T12:00:00Z")
            fixtures.lesson(maths, listOf(anya), tomorrow)
            fixtures.lesson(maths, listOf(boris), env.clock.now().minus(3600.seconds))
            fixtures.lesson(maths, listOf(boris), borisNext)
            val archived = fixtures.archivedStudent("Вера")
            val filter = MutableStateFlow<Assignee?>(null)
            val state = backgroundScope.watch(filter)
            runCurrent()

            val choices = state.value!!.choices(env.clock.now())

            assertEquals(listOf("Борис", "Аня"), choices.map { it.name })
            assertEquals(borisNext, choices.first().nextLessonAt)
            assertEquals(tomorrow, choices.last().nextLessonAt)
            assertTrue(choices.none { it.assignee == Assignee.Student(archived) })
        }

    @Test
    fun `with nobody to set it for there is nothing to choose`() = runTest {
        val filter = MutableStateFlow<Assignee?>(null)
        val state = backgroundScope.watch(filter)
        runCurrent()

        assertTrue(state.value!!.choices(env.clock.now()).isEmpty())
    }

    /**
     * Opened for correcting, the homework's own assignee is always on offer — or the dialog, preselecting
     * from the list, would save it onto somebody else.
     */
    @Test
    fun `correcting homework offers whom it is for even when they are no longer offered`() =
        runTest {
            val anya = fixtures.student("Аня")
            val boris = fixtures.student("Борис")
            fixtures.homework(Assignee.Student(anya))
            val filter = MutableStateFlow<Assignee?>(null)
            val state = backgroundScope.watch(filter)
            runCurrent()
            val row = state.value!!.lists.current.single()
            env.studentRepo.setArchived(env.studentRepo.getById(anya)!!, archived = true)
            runCurrent()

            val choices = query.choicesFor(row)

            assertEquals(listOf(Assignee.Student(anya), Assignee.Student(boris)), choices.map { it.assignee })
        }

    @Test
    fun `at midnight tomorrow's homework becomes today's`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        fixtures.lesson(maths, listOf(anya), tomorrow)
        fixtures.homework(Assignee.Student(anya))
        val filter = MutableStateFlow<Assignee?>(null)
        val state = backgroundScope.watch(filter)
        runCurrent()
        assertEquals(HomeworkRules.Due.LATER, state.value!!.lists.current.single().due)
        val due = query.observeDue().stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())
        runCurrent()
        assertTrue(due.value.isEmpty())

        val untilMidnight = 9.hours + 1.minutes
        env.clock.advanceBy(untilMidnight.inWholeMilliseconds)
        advanceTimeBy(untilMidnight.inWholeMilliseconds)
        runCurrent()

        assertEquals(HomeworkRules.Due.TODAY, state.value!!.lists.current.single().due)
        assertEquals(state.value!!.lists.current, due.value)
    }

    @Test
    fun `without homework the filter is empty but everybody can receive a first assignment`() =
        runTest {
            val anya = fixtures.student("Аня")
            val group = fixtures.group(name = "9 «Б»", members = setOf(anya))
            val filter = MutableStateFlow<Assignee?>(null)
            val state = backgroundScope.watch(filter)
            runCurrent()

            assertTrue(state.value!!.filterAssignees.isEmpty())
            assertEquals(
                setOf(Assignee.Student(anya), Assignee.Group(group)),
                state.value!!.choices(env.clock.now()).map { it.assignee }.toSet(),
            )
        }

    @Test
    fun `the filter offers only people with current or archived homework in name order`() =
        runTest {
            val boris = fixtures.student("Борис")
            val anya = fixtures.student("Аня")
            val withoutHomework = fixtures.student("Вера")
            val archived = fixtures.archivedStudent("Глеб")
            fixtures.homework(Assignee.Student(boris), done = true)
            fixtures.homework(Assignee.Student(anya))
            fixtures.homework(Assignee.Student(archived))
            fixtures.group(name = "Без заданий")
            val filter = MutableStateFlow<Assignee?>(null)
            val state = backgroundScope.watch(filter)
            runCurrent()

            val expected = listOf(Assignee.Student(anya), Assignee.Student(boris))
            assertEquals(expected, state.value!!.filterAssignees)
            assertTrue(state.value!!.choices(env.clock.now()).any { it.assignee == Assignee.Student(withoutHomework) })

            filter.value = Assignee.Student(anya)
            runCurrent()
            assertEquals(
                expected,
                state.value!!.filterAssignees,
                "narrowing the list keeps the other filter options",
            )
        }

    @Test
    fun `group homework offers the group and its active students in the filter`() =
        runTest {
            val anya = fixtures.student("Аня")
            val boris = fixtures.student("Борис")
            val archived = fixtures.archivedStudent("Глеб")
            fixtures.student("Вера")
            val group = fixtures.group(name = "9 «Б»", members = setOf(anya, boris, archived))
            val withoutHomework = fixtures.group(name = "Без заданий", members = setOf(anya))
            fixtures.homework(Assignee.Group(group), done = true)
            val filter = MutableStateFlow<Assignee?>(null)
            val state = backgroundScope.watch(filter)
            runCurrent()

            assertEquals(
                listOf(Assignee.Student(anya), Assignee.Student(boris), Assignee.Group(group)),
                state.value!!.filterAssignees,
            )

            env.groupRepo.setArchived(env.groupRepo.getById(group)!!, archived = true)
            runCurrent()
            assertTrue(state.value!!.filterAssignees.isEmpty())
            assertTrue(state.value!!.choices(env.clock.now()).any { it.assignee == Assignee.Group(withoutHomework) })
        }

    @Test
    fun `the filter follows homework additions edits completion and deletion`() = runTest {
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val filter = MutableStateFlow<Assignee?>(null)
        val state = backgroundScope.watch(filter)
        runCurrent()

        repo.give(Assignee.Student(anya), "§ 12")
        runCurrent()
        assertEquals(listOf(Assignee.Student(anya)), state.value!!.filterAssignees)

        val item = state.value!!.lists.current.single().homework
        repo.setDone(item, done = true)
        runCurrent()
        assertEquals(listOf(Assignee.Student(anya)), state.value!!.filterAssignees)

        repo.edit(item, Assignee.Student(boris), "§ 13")
        runCurrent()
        assertEquals(listOf(Assignee.Student(boris)), state.value!!.filterAssignees)

        filter.value = Assignee.Student(boris)
        runCurrent()
        repo.delete(state.value!!.lists.archive.single().homework)
        runCurrent()
        assertTrue(state.value!!.filterAssignees.isEmpty())
        assertEquals("Борис", state.value!!.filterName)

        filter.value = null
        runCurrent()
        assertNull(state.value!!.filterName)
    }

    @Test
    fun `filtered to a student the tab shows theirs and their group's`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
        fixtures.homework(Assignee.Student(anya), text = "Ани")
        fixtures.homework(Assignee.Group(group), text = "группы", done = true)
        fixtures.homework(Assignee.Student(boris), text = "Бориса")
        val filter = MutableStateFlow<Assignee?>(null)
        val state = backgroundScope.watch(filter)
        runCurrent()

        filter.value = Assignee.Student(anya)
        runCurrent()

        assertEquals("Аня", state.value!!.filterName)
        assertEquals(listOf("Ани"), state.value!!.lists.current.map { it.text })
        assertEquals(listOf("группы"), state.value!!.lists.archive.map { it.text })
    }

    @Test
    fun `filtered to a group and then cleared the tab shows the group's and then everybody's`() =
        runTest {
            val maths = fixtures.subject()
            val anya = fixtures.pricedStudent("Аня", maths)
            val group = fixtures.group(name = "9 «Б»", subjectId = maths, members = setOf(anya))
            fixtures.homework(Assignee.Student(anya), text = "Ани")
            fixtures.homework(Assignee.Group(group), text = "группы")
            val filter = MutableStateFlow<Assignee?>(null)
            val state = backgroundScope.watch(filter)
            runCurrent()

            filter.value = Assignee.Group(group)
            runCurrent()
            assertEquals(listOf("группы"), state.value!!.lists.current.map { it.text })

            filter.value = null
            runCurrent()
            assertNull(state.value!!.filterName)
            assertEquals(setOf("Ани", "группы"), state.value!!.lists.current.map { it.text }.toSet())
        }
}
