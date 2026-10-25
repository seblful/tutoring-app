package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HomeworkRepositoryTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()
    private val repo = env.homeworkRepo
    private val fixtures = env.fixtures

    private val now: Instant = env.clock.now()
    private val tomorrow: Instant = Instant.parse("2026-06-02T12:00:00Z")

    @Test
    fun `homework is given now - its text trimmed - and current`() = runTest {
        val anya = fixtures.student("Аня")

        val given = repo.give(Assignee.Student(anya), "  § 12, упр. 4–7 \n")

        val stored = repo.observeAll().first().single()
        assertEquals(given, stored)
        assertEquals("§ 12, упр. 4–7", stored.text)
        assertEquals(now, stored.givenAt)
        assertNull(stored.doneAt)
    }

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

    @Test
    fun `an edit from a stale copy does not undo a swipe made since`() = runTest {
        val anya = fixtures.student("Аня")
        val stale = fixtures.homework(Assignee.Student(anya))
        repo.setDone(stale, done = true)

        repo.edit(stale, stale.assignee, "§ 13")

        assertTrue(repo.observeAll().first().single().isDone)
    }

    @Test
    fun `done is stamped now - and current again clears it`() = runTest {
        val anya = fixtures.student("Аня")
        val given = fixtures.homework(Assignee.Student(anya), at = now.minus((86_400).seconds))

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

    @Test
    fun `a student's homework includes their groups' - while they are members`() = runTest {
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

    @Test
    fun `homework writes update the calendar without rearming phone reminders`() = runTest {
        fixtures.withCalendarSync()
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        fixtures.lesson(maths, listOf(anya), tomorrow)
        env.calendar.calls.clear()
        val refreshes = env.reminders.refreshCount

        val given = repo.give(Assignee.Student(anya), "§ 12")
        assertEquals("§ 12", env.calendar.calls.last().description)
        repo.edit(given, given.assignee, "§ 13")
        assertEquals("§ 13", env.calendar.calls.last().description)
        repo.setDone(given, done = true)
        assertEquals("", env.calendar.calls.last().description)
        repo.putBack(given)
        assertEquals("§ 13", env.calendar.calls.last().description)
        repo.delete(given)
        assertEquals("", env.calendar.calls.last().description)
        repo.restore(given)
        assertEquals("§ 12", env.calendar.calls.last().description)

        assertEquals(refreshes, env.reminders.refreshCount)
    }

    /** Undoing «Вернуть» puts the piece back where it was in the archive, not at its top. */
    @Test
    fun `putting a swipe back restores when it was done`() = runTest {
        val anya = fixtures.student("Аня")
        val lastWeek = now.minus((7 * 86_400).seconds)
        val done = fixtures.homework(Assignee.Student(anya), at = lastWeek, done = true)
        repo.setDone(done, done = false)

        repo.putBack(done)

        assertEquals(lastWeek, repo.observeAll().first().single().doneAt)
    }
}
