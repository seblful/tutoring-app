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
class HomeworkAttachmentTest {
    private val env = TestEnv()
    private val query = env.store().homeworkQuery
    private val repo = env.homeworkRepo
    private val fixtures = env.fixtures
    private val now = env.clock.now()
    private val tomorrow = Instant.parse("2026-06-02T12:00:00Z")
    private val nextWeek = Instant.parse("2026-06-08T12:00:00Z")

    @AfterTest
    fun close() = env.close()

    @Test
    fun `homework given now is attached to the next lesson from now`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        fixtures.lesson(maths, listOf(anya), now.minus((86_400).seconds))
        fixtures.lesson(maths, listOf(anya), nextWeek)
        val next = fixtures.lesson(maths, listOf(anya), tomorrow).single()
        repo.give(Assignee.Student(anya), "§ 12")

        val loaded = query.observe().first().lists

        assertEquals(env.classRepo.getById(next)!!.start, loaded.current.single().lessonAt)
    }

    @Test
    fun `the lessons read reach back to the oldest current homework`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val march = Instant.parse("2026-03-02T12:00:00Z")
        val lessonInMarch = fixtures.lesson(maths, listOf(anya), march.plus((86_400).seconds)).single()
        fixtures.homework(Assignee.Student(anya), at = march)

        val loaded = query.observe().first().lists

        assertEquals(env.classRepo.getById(lessonInMarch)!!.start, loaded.current.single().lessonAt)
    }

    @Test
    fun `a lesson's current homework is the group's and its members' own - not what is done`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(subjectId = maths, members = setOf(anya, boris))
        val lesson = fixtures.lesson(maths, listOf(anya, boris), tomorrow, groupId = group).first()
        val groups = fixtures.homework(Assignee.Group(group), text = "группы")
        val anyas = fixtures.homework(Assignee.Student(anya), text = "Ани")
        fixtures.homework(Assignee.Student(boris), text = "сделано", done = true)

        val current = query.currentAttachedTo(env.classRepo.getSessionFor(lesson)!!)

        assertEquals(setOf(groups, anyas), current.toSet())
    }

    /**
     * A member left out of one of their group's lessons has no class in it — and the group's homework is
     * still that lesson's on their card, as it is in the tab.
     */
    @Test
    fun `a student's card attaches their group's homework to the group's lesson - even one they are not in`() =
        runTest {
            val maths = fixtures.subject()
            val anya = fixtures.pricedStudent("Аня", maths)
            val boris = fixtures.pricedStudent("Борис", maths)
            val group = fixtures.group(subjectId = maths, members = setOf(anya, boris))
            val withoutAnya = fixtures.lesson(maths, listOf(boris), tomorrow, groupId = group).single()
            fixtures.lesson(maths, listOf(anya, boris), nextWeek, groupId = group)
            fixtures.homework(Assignee.Group(group))

            val loaded = query.observeForStudent(anya).first()

            assertEquals(env.classRepo.getById(withoutAnya)!!.start, loaded.current.single().lessonAt)
        }

    /** Bringing back an older piece widens the window, and the piece arrives attached to its own lesson. */
    @Test
    fun `an older piece brought back is attached to the lesson it was for`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val march = Instant.parse("2026-03-02T12:00:00Z")
        val inMarch = fixtures.lesson(maths, listOf(anya), march.plus((86_400).seconds)).single()
        fixtures.lesson(maths, listOf(anya), tomorrow)
        val old = fixtures.homework(Assignee.Student(anya), at = march, done = true)
        query.observe().first().lists

        repo.setDone(old, done = false)

        val loaded = query.observe().first().lists
        assertEquals(env.classRepo.getById(inMarch)!!.start, loaded.current.single().lessonAt)
    }

    @Test
    fun `old homework attaches to its owner despite other historical lessons`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val march = Instant.parse("2026-03-02T12:00:00Z")
        val anyasInMarch = fixtures.lesson(maths, listOf(anya), march.plus((86_400).seconds)).single()
        fixtures.lesson(maths, listOf(boris), march.plus((30 * 86_400).seconds)).single()
        fixtures.homework(Assignee.Student(anya), at = march)

        val loaded = query.observe().first().lists

        assertEquals(env.classRepo.getById(anyasInMarch)!!.start, loaded.current.single().lessonAt)
    }
}
