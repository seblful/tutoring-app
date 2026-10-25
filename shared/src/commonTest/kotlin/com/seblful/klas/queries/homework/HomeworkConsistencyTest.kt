package com.seblful.klas.queries.homework

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class HomeworkConsistencyTest {
    private val env = TestEnv()
    private val store = env.store()
    private val query = store.homeworkQuery
    private val fixtures = env.fixtures
    private val tomorrow = env.clock.now() + 1.days

    @AfterTest
    fun close() = env.close()

    private fun <T> CoroutineScope.watch(flow: Flow<T>): StateFlow<T?> =
        flow.stateIn(this, SharingStarted.Eagerly, null)

    private fun description(classId: Long): String =
        env.calendar.calls.last { it.klass.id == classId }.description

    @Test
    fun `cards lists sheets reminders and invitations follow the same attachment changes`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val ann = fixtures.pricedStudent("Ann", subject)
        val boris = fixtures.pricedStudent("Boris", subject)
        val group = fixtures.group("Group", subject, setOf(ann, boris))
        val first = fixtures.lesson(subject, listOf(boris), tomorrow, groupId = group).single()
        val second = fixtures.lesson(subject, listOf(ann, boris), tomorrow + 1.days, groupId = group)
        val shared = store.homework.give(Assignee.Group(group), "Shared")
        env.clock.advanceBy(1000)
        val personal = store.homework.give(Assignee.Student(ann), "Personal")
        val board = backgroundScope.watch(query.observe(flowOf(Assignee.Student(ann))))
        val student = backgroundScope.watch(store.studentDetails.observe(ann))
        val grouped = backgroundScope.watch(store.groupDetails.observe(group))
        runCurrent()

        // Ann's card includes the group's work even though she is not booked into its first lesson.
        assertEquals(tomorrow, board.value!!.lists.current.first().lessonAt)
        assertEquals(board.value!!.lists, student.value!!.homework)
        assertEquals(listOf(shared), grouped.value!!.homework.current.map { it.homework })
        assertEquals(listOf(shared), query.currentAttachedTo(store.classes.getSessionFor(first)!!))
        assertEquals("Shared", description(first))
        assertEquals("Personal", description(second[0]))
        assertEquals("", description(second[1]))

        fixtures.setStatus(first, LessonStatus.CANCELLED)
        runCurrent()
        assertEquals(setOf(tomorrow + 1.days), board.value!!.lists.current.map { it.lessonAt }.toSet())
        assertEquals(board.value!!.lists, student.value!!.homework)
        assertEquals(tomorrow + 1.days, grouped.value!!.homework.current.single().lessonAt)
        assertEquals("Shared\n\nPersonal", description(second[0]))
        assertEquals("Shared", description(second[1]))

        val moved = tomorrow + 3.days
        store.classes.move(store.classes.getSessionFor(second[0])!!, moved, env.clock.now()).written()
        runCurrent()
        val session = store.classes.getSessionFor(second[0])!!
        assertEquals(setOf(moved), board.value!!.lists.current.map { it.lessonAt }.toSet())
        assertEquals(board.value!!.lists, student.value!!.homework)
        assertEquals(moved, grouped.value!!.homework.current.single().lessonAt)
        assertEquals(setOf(shared, personal), query.currentAttachedTo(session).toSet())
        assertEquals(setOf(shared, personal), query.observeAttachedTo(session).first().toSet())

        store.homework.edit(shared, Assignee.Student(ann), "Reassigned")
        runCurrent()
        assertTrue(grouped.value!!.homework.current.isEmpty())
        assertEquals(board.value!!.lists, student.value!!.homework)
        assertEquals("Reassigned\n\nPersonal", description(second[0]))
        assertEquals("", description(second[1]))

        val reassigned = board.value!!.lists.current.single { it.homework.id == shared.id }.homework
        store.homework.setDone(reassigned, done = true)
        runCurrent()
        assertEquals(listOf(personal), query.currentAttachedTo(session))
        assertEquals(setOf(shared.id, personal.id), query.observeAttachedTo(session).first().map { it.id }.toSet())
        assertEquals(listOf(shared.id), board.value!!.lists.archive.map { it.homework.id })
        assertEquals(board.value!!.lists, student.value!!.homework)
        assertEquals("Personal", description(second[0]))
    }

    @Test
    fun `archived owners leave the board and Today while their cards retain current and done work`() = runTest {
        val subject = fixtures.subject()
        val ann = fixtures.pricedStudent("Ann", subject)
        val group = fixtures.group("Group", subject, setOf(ann))
        fixtures.lesson(subject, listOf(ann), env.clock.now() + 1.hours, groupId = group)
        val own = fixtures.homework(Assignee.Student(ann))
        val shared = fixtures.homework(Assignee.Group(group), done = true)
        assertEquals(listOf(own), query.observeDue().first().map { it.homework })
        store.students.setArchived(store.students.getById(ann)!!, archived = true)
        store.groups.setArchived(store.groups.getById(group)!!, archived = true)

        assertTrue(query.observe().first().lists.current.isEmpty())
        assertTrue(query.observe().first().lists.archive.isEmpty())
        assertTrue(query.observeDue().first().isEmpty())
        assertEquals(listOf(own), store.studentDetails.observe(ann).first().homework.current.map { it.homework })
        assertEquals(listOf(shared), store.studentDetails.observe(ann).first().homework.archive.map { it.homework })
        assertEquals(listOf(shared), store.groupDetails.observe(group).first().homework.archive.map { it.homework })
    }
}
