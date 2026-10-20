package com.seblful.klas.work

import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.RecordingNotifier
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MaintenanceTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()
    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")

    private fun maintenance() = env.maintenance

    private suspend fun seedClass(start: Instant, status: LessonStatus = LessonStatus.SCHEDULED): Long {
        val subjectId = env.fixtures.subject("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        return env.fixtures.lesson(subjectId = subjectId, students = listOf(studentId), start = start)
            .single()
            .also { if (status != LessonStatus.SCHEDULED) env.fixtures.setStatus(it, status) }
    }

    @Test
    fun `auto-completes a finished class when the setting is on`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minus(7200.seconds)) // ended an hour ago
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().run(now)

        assertEquals(LessonStatus.COMPLETED, env.classRepo.getById(id)?.status)
    }

    @Test
    fun `leaves a finished class alone when auto-complete is off`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minus(7200.seconds))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = false) }

        maintenance().run(now)

        assertEquals(LessonStatus.SCHEDULED, env.classRepo.getById(id)?.status)
    }

    @Test
    fun `arms a reminder for an upcoming class`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.plus((4 * 3600).seconds)) // in four hours, well past the 60-min lead

        maintenance().run(now)

        assertEquals(listOf(id), env.reminders.armed.map { it.classId })
    }

    @Test
    fun `re-planning replaces the previous set of reminders rather than adding to it`() =
        runTest(mainRule.dispatcher) {
            seedClass(now.plus((4 * 3600).seconds))

            val maintenance = maintenance()
            maintenance.run(now)
            maintenance.run(now)

            assertEquals("two passes, two hand-overs", 2, env.reminders.schedules.size)
            assertEquals("but only one reminder in force", 1, env.reminders.armed.size)
        }

    @Test
    fun `a class auto-completed in this same pass is not then given a reminder`() =
        runTest(mainRule.dispatcher) {
            seedClass(now.minus(7200.seconds))
            env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

            maintenance().run(now)

            assertTrue(env.reminders.armed.isEmpty())
        }

    @Test
    fun `a lesson exactly on the horizon still earns a reminder`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.plus(Reminders.DEFAULT_HORIZON))

        maintenance().run(now)

        assertEquals(listOf(id), env.reminders.armed.map { it.classId })
    }

    @Test
    fun `a lesson in progress stays owed through a pass`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minus(600.seconds))

        maintenance().run(now)

        assertEquals(listOf(id to true), env.reminders.armed.map { it.classId to it.due })
    }

    @Test
    fun `a lesson past the horizon earns none`() = runTest(mainRule.dispatcher) {
        seedClass(now.plus(Reminders.DEFAULT_HORIZON).plus(1.milliseconds))

        maintenance().run(now)

        assertTrue(env.reminders.armed.isEmpty())
    }

    @Test
    fun `a pass reports how many lessons it settled`() = runTest(mainRule.dispatcher) {
        seedClass(now.minus(7200.seconds))
        seedClass(now.minus(3600.seconds))
        seedClass(now.plus(3600.seconds)) // still to come, so not settled by this pass
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals(2, maintenance().run(now).lessons)
    }

    @Test
    fun `a group lesson counts once, however many it billed`() = runTest(mainRule.dispatcher) {
        val subjectId = env.fixtures.subject("Математика")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.student(it) }
        val groupId = env.fixtures.group(subjectId = subjectId, members = members.toSet())
        val ids = env.fixtures.lesson(subjectId, members, start = now.minus(7200.seconds), groupId = groupId)
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals("one finished hour", 1, maintenance().run(now).lessons)
        val session = env.classRepo.getSessionFor(ids.first())!!
        assertEquals("billed to all three of them", 3, session.size)
    }

    @Test
    fun `what a pass billed leaves out a member excused from the lesson`() = runTest(mainRule.dispatcher) {
        val subjectId = env.fixtures.subject("Математика")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.student(it) }
        val groupId = env.fixtures.group(subjectId = subjectId, members = members.toSet())
        val ids = env.fixtures.lesson(subjectId, members, start = now.minus(7200.seconds), groupId = groupId)
        env.fixtures.setAttendance(ids.last(), Attended.ABSENT)
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals("two of three at 30 for the hour", Money.ofMajor(60), maintenance().run(now).billed)
    }

    @Test
    fun `a pass that settles nothing reports nothing`() = runTest(mainRule.dispatcher) {
        seedClass(now.minus(7200.seconds))
        assertEquals(0, maintenance().run(now).lessons)
    }

    @Test
    fun `the backlog is countable while auto-completion is still off`() = runTest(mainRule.dispatcher) {
        seedClass(now.minus(7200.seconds))
        seedClass(now.minus(3600.seconds))
        seedClass(now.plus(3600.seconds))

        assertEquals(2, maintenance().dueForCompletion(now))
        assertEquals("and counting it settled nothing", 0, maintenance().run(now).lessons)
    }

    @Test
    fun `an unwatched pass says what it billed`() = runTest(mainRule.dispatcher) {
        seedClass(now.minus(7200.seconds))
        seedClass(now.minus(3600.seconds))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().runUnwatched(now)

        assertEquals(
            listOf(RecordingNotifier.AutoCompleted(2, Money.ofMajor(60), LocalDate(2026, 6, 15))),
            env.notifier.autoCompleted,
        )
    }

    @Test
    fun `an unwatched pass names the latest day it settled`() = runTest(mainRule.dispatcher) {
        seedClass(now.minus(3.days))
        seedClass(now.minus(1.days))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().runUnwatched(now)

        assertEquals(LocalDate(2026, 6, 14), env.notifier.autoCompleted.single().day)
    }

    @Test
    fun `an unwatched pass that settles nothing stays quiet`() = runTest(mainRule.dispatcher) {
        seedClass(now.minus(7200.seconds))
        maintenance().runUnwatched(now)

        assertTrue(env.notifier.autoCompleted.isEmpty())
    }

    @Test
    fun `a watched pass reports to the screen and not to the tray`() = runTest(mainRule.dispatcher) {
        seedClass(now.minus(7200.seconds))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        val settled = maintenance().run(now)

        assertEquals(1, settled.lessons)
        assertTrue("nothing posted", env.notifier.autoCompleted.isEmpty())
    }

    @Test
    fun `a pass plans the digests the tutor has switched on`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update {
            it.copy(
                dailyAgendaEnabled = true,
                agendaMinuteOfDay = 9 * 60,
                debtDigestEnabled = true,
                debtDayOfWeek = 1,
                debtMinuteOfDay = 9 * 60,
            )
        }

        maintenance().run(now)

        val planned = env.digestScheduler.planned
        assertEquals(Instant.parse("2026-06-16T06:00:00Z"), planned.agendaAt)
        assertEquals(Instant.parse("2026-06-22T06:00:00Z"), planned.debtsAt)
    }

    @Test
    fun `a pass plans nothing for a digest that is off`() = runTest(mainRule.dispatcher) {
        maintenance().run(now)

        assertEquals(
            Digests.Plan(agendaAt = null, unfinishedAt = null, debtsAt = null),
            env.digestScheduler.planned,
        )
    }

    @Test
    fun `the lead time comes from settings`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.plus((90 * 60).seconds))
        env.settingsRepo.update { it.copy(reminderLeadMinutes = 120) }

        maintenance().run(now)

        val planned = env.reminders.armed.single()
        assertEquals(id, planned.classId)
        assertTrue("its lead moment has already passed", planned.due)
    }
}
