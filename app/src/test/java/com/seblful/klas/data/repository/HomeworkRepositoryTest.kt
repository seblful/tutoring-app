package com.seblful.klas.data.repository

import com.seblful.klas.domain.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * [HomeworkRepository]: what a write stores and what it leaves alone, the reads that pair homework with
 * its lessons, and that none of it reaches the calendar or the reminders.
 */
@RunWith(RobolectricTestRunner::class)
class HomeworkRepositoryTest {

    @get:Rule
    val env = TestEnv()
    private val repo = env.homeworkRepo
    private val fixtures = env.fixtures

    // The environment's clock reads 2026-06-01T12:00Z.
    private val now: Instant = env.clock.instant()
    private val tomorrow: Instant = Instant.parse("2026-06-02T12:00:00Z")
    private val nextWeek: Instant = Instant.parse("2026-06-08T12:00:00Z")

    @Test
    fun `homework is given now, its text trimmed, and current`() = runTest {
        val anya = fixtures.student("Аня")

        val given = repo.give(Assignee.Student(anya), "  § 12, упр. 4–7 \n")

        val stored = repo.observeAll().first().single()
        assertEquals(given, stored)
        assertEquals("§ 12, упр. 4–7", stored.text)
        assertEquals(now, stored.givenAt)
        assertNull(stored.doneAt)
    }

    /** What the rules attach it to is the student's next lesson from the minute it was given. */
    @Test
    fun `homework given now is attached to the next lesson from now`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        fixtures.lesson(maths, listOf(anya), now.minusSeconds(86_400))
        fixtures.lesson(maths, listOf(anya), nextWeek)
        val next = fixtures.lesson(maths, listOf(anya), tomorrow).single()
        repo.give(Assignee.Student(anya), "§ 12")

        val loaded = repo.observeWithLessons(since = now).first()

        val current = HomeworkRules.lists(loaded.homework, loaded.classes).current.single()
        assertEquals(next, current.lesson?.id)
    }

    /**
     * Homework left undone for months is still attached to the lesson it was for — so the window reaches
     * back to it, whatever the caller asked for.
     */
    @Test
    fun `the lessons read reach back to the oldest current homework`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val march = Instant.parse("2026-03-02T12:00:00Z")
        val lessonInMarch = fixtures.lesson(maths, listOf(anya), march.plusSeconds(86_400)).single()
        fixtures.homework(Assignee.Student(anya), at = march)

        val loaded = repo.observeWithLessons(since = now).first()

        assertEquals(lessonInMarch, HomeworkRules.lists(loaded.homework, loaded.classes).current.single().lesson?.id)
    }

    /** Correcting the text neither moves it to a later lesson nor un-does it. */
    @Test
    fun `an edit keeps when it was given and whether it is done`() = runTest {
        val anya = fixtures.student("Аня")
        val boris = fixtures.student("Борис")
        val given = fixtures.homework(Assignee.Student(anya), text = "§ 12", done = true)
        env.clock.advanceBy(3_600_000)

        repo.edit(given, Assignee.Student(boris), " § 13 ")

        val stored = repo.observeAll().first().single()
        assertEquals(Assignee.Student(boris), stored.assignee)
        assertEquals("§ 13", stored.text)
        assertEquals(given.givenAt, stored.givenAt)
        assertEquals(given.doneAt, stored.doneAt)
    }

    /** An edit written from a dialog opened before the swipe must not put the swipe back. */
    @Test
    fun `an edit from a stale copy does not undo a swipe made since`() = runTest {
        val anya = fixtures.student("Аня")
        val stale = fixtures.homework(Assignee.Student(anya))
        repo.setDone(stale, done = true)

        repo.edit(stale, stale.assignee, "§ 13")

        assertTrue(repo.observeAll().first().single().isDone)
    }

    @Test
    fun `done is stamped now, and current again clears it`() = runTest {
        val anya = fixtures.student("Аня")
        val given = fixtures.homework(Assignee.Student(anya), at = now.minusSeconds(86_400))

        repo.setDone(given, done = true)
        assertEquals(now, repo.observeAll().first().single().doneAt)

        repo.setDone(given, done = false)
        assertNull(repo.observeAll().first().single().doneAt)
    }

    @Test
    fun `a deleted piece of homework comes back as it was`() = runTest {
        val anya = fixtures.student("Аня")
        val given = fixtures.homework(Assignee.Student(anya), done = true)

        repo.delete(given)
        assertTrue(repo.observeAll().first().isEmpty())

        repo.restore(given)
        assertEquals(listOf(given), repo.observeAll().first())
    }

    /** A student's card lists their group's homework — for as long as they are in the group. */
    @Test
    fun `a student's homework includes their groups', while they are members`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(subjectId = maths, members = setOf(anya, boris))
        val own = fixtures.homework(Assignee.Student(anya), text = "своё")
        val groups = fixtures.homework(Assignee.Group(group), text = "группы")
        fixtures.homework(Assignee.Student(boris), text = "Бориса")

        assertEquals(setOf(own, groups), repo.observeForStudent(anya).first().toSet())

        env.groupRepo.save(env.groupRepo.getById(group)!!, memberIds = setOf(boris))
        assertEquals(listOf(own), repo.observeForStudent(anya).first())
    }

    /** The reminder names what is still to be done, and a group lesson's gathers a member's own too. */
    @Test
    fun `a lesson's current homework is the group's and its members' own, not what is done`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(subjectId = maths, members = setOf(anya, boris))
        val lesson = fixtures.lesson(maths, listOf(anya, boris), tomorrow, groupId = group).first()
        val groups = fixtures.homework(Assignee.Group(group), text = "группы")
        val anyas = fixtures.homework(Assignee.Student(anya), text = "Ани")
        fixtures.homework(Assignee.Student(boris), text = "сделано", done = true)

        val current = repo.currentAttachedTo(env.classRepo.getSessionFor(lesson)!!)

        assertEquals(setOf(groups, anyas), current.toSet())
    }

    /** Homework is on no event and in no plan, so its writes owe the calendar and the reminders nothing. */
    @Test
    fun `homework writes touch neither the calendar nor the reminders`() = runTest {
        fixtures.withCalendarSync()
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        fixtures.lesson(maths, listOf(anya), tomorrow)
        env.calendar.calls.clear()
        val refreshes = env.reminders.refreshCount

        val given = repo.give(Assignee.Student(anya), "§ 12")
        repo.edit(given, given.assignee, "§ 13")
        repo.setDone(given, done = true)
        repo.delete(given)
        repo.restore(given)

        assertTrue(env.calendar.calls.isEmpty())
        assertEquals(refreshes, env.reminders.refreshCount)
    }

    /** Undoing «Вернуть» puts the piece back where it was in the archive, not at its top. */
    @Test
    fun `putting a swipe back restores when it was done`() = runTest {
        val anya = fixtures.student("Аня")
        val lastWeek = now.minusSeconds(7 * 86_400)
        val done = fixtures.homework(Assignee.Student(anya), at = lastWeek, done = true)
        repo.setDone(done, done = false)

        repo.putBack(done)

        assertEquals(lastWeek, repo.observeAll().first().single().doneAt)
    }

    /**
     * A member left out of one of their group's lessons has no class in it — and the group's homework is
     * still that lesson's on their card, as it is in the tab.
     */
    @Test
    fun `a student's card attaches their group's homework to the group's lesson, even one they are not in`() =
        runTest {
            val maths = fixtures.subject()
            val anya = fixtures.pricedStudent("Аня", maths)
            val boris = fixtures.pricedStudent("Борис", maths)
            val group = fixtures.group(subjectId = maths, members = setOf(anya, boris))
            val withoutAnya = fixtures.lesson(maths, listOf(boris), tomorrow, groupId = group).single()
            fixtures.lesson(maths, listOf(anya, boris), nextWeek, groupId = group)
            fixtures.homework(Assignee.Group(group))

            val loaded = repo.observeForStudentWithLessons(anya).first()

            val lesson = HomeworkRules.lists(loaded.homework, loaded.classes).current.single().lesson
            assertEquals(env.classRepo.getById(withoutAnya)!!.lessonId, lesson?.lessonId)
        }

    /** Bringing back an older piece widens the window, and the piece arrives attached to its own lesson. */
    @Test
    fun `an older piece brought back is attached to the lesson it was for`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val march = Instant.parse("2026-03-02T12:00:00Z")
        val inMarch = fixtures.lesson(maths, listOf(anya), march.plusSeconds(86_400)).single()
        fixtures.lesson(maths, listOf(anya), tomorrow)
        val old = fixtures.homework(Assignee.Student(anya), at = march, done = true)
        repo.observeWithLessons(since = now).first()

        repo.setDone(old, done = false)

        val loaded = repo.observeWithLessons(since = now).first()
        assertEquals(inMarch, HomeworkRules.lists(loaded.homework, loaded.classes).current.single().lesson?.id)
    }
}
