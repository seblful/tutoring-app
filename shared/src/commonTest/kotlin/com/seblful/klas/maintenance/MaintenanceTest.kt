package com.seblful.klas.maintenance

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceTest {

    private val env = TestEnv()

    @AfterTest
    fun close() = env.close()
    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")

    private fun maintenance() = env.maintenance

    private suspend fun seedClass(start: Instant, status: LessonStatus = LessonStatus.SCHEDULED): Long {
        val subjectId = env.subjectRepo.createOrReuse("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        return env.fixtures.lesson(subjectId = subjectId, students = listOf(studentId), start = start)
            .single()
            .also { if (status != LessonStatus.SCHEDULED) env.fixtures.setStatus(it, status) }
    }

    @Test
    fun `auto-completes a finished class when the setting is on`() = runTest {
        val id = seedClass(now.minus(7200.seconds)) // ended an hour ago
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().run(now)

        assertEquals(LessonStatus.COMPLETED, env.classRepo.getById(id)?.status)
    }

    @Test
    fun `leaves a finished class alone when auto-complete is off`() = runTest {
        val id = seedClass(now.minus(7200.seconds))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = false) }

        maintenance().run(now)

        assertEquals(LessonStatus.SCHEDULED, env.classRepo.getById(id)?.status)
    }

    @Test
    fun `arms a reminder for an upcoming class`() = runTest {
        val id = seedClass(now.plus((4 * 3600).seconds)) // in four hours, well past the 60-min lead

        maintenance().run(now)

        assertEquals(listOf(id), env.reminders.armed.map { it.classId })
    }

    @Test
    fun `re-planning replaces the previous set of reminders rather than adding to it`() =
        runTest {
            seedClass(now.plus((4 * 3600).seconds))

            val maintenance = maintenance()
            maintenance.run(now)
            maintenance.run(now)

            assertEquals(2, env.reminders.schedules.size, "two passes, two hand-overs")
            assertEquals(1, env.reminders.armed.size, "but only one reminder in force")
        }

    @Test
    fun `a class auto-completed in this same pass is not then given a reminder`() =
        runTest {
            seedClass(now.minus(7200.seconds))
            env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

            maintenance().run(now)

            assertTrue(env.reminders.armed.isEmpty())
        }

    @Test
    fun `with auto-completion on a pass arranges the next one for the next lesson end`() =
        runTest {
            seedClass(now.minus(600.seconds)) // running, ends in 50 minutes
            seedClass(now.plus((4 * 3600).seconds))
            env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

            maintenance().run(now)

            assertEquals(now.plus(3000.seconds), env.reminders.nextPass)
        }

    @Test
    fun `with auto-completion off a pass arranges no extra one`() = runTest {
        seedClass(now.plus((4 * 3600).seconds))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }
        maintenance().run(now)
        env.settingsRepo.update { it.copy(autoCompleteEnabled = false) }

        maintenance().run(now)

        assertEquals(null, env.reminders.nextPass)
    }

    @Test
    fun `a lesson exactly on the horizon still earns a reminder`() = runTest {
        val id = seedClass(now.plus(Reminders.planningHorizon(Reminders.DEFAULT_LEAD)))

        maintenance().run(now)

        assertEquals(listOf(id), env.reminders.armed.map { it.classId })
    }

    @Test
    fun `a lesson in progress stays owed through a pass`() = runTest {
        val id = seedClass(now.minus(600.seconds))

        maintenance().run(now)

        assertEquals(listOf(id to true), env.reminders.armed.map { it.classId to it.due })
    }

    @Test
    fun `a lesson past the horizon earns none`() = runTest {
        seedClass(now.plus(Reminders.planningHorizon(Reminders.DEFAULT_LEAD)).plus(1.milliseconds))

        maintenance().run(now)

        assertTrue(env.reminders.armed.isEmpty())
    }

    @Test
    fun `a pass reports how many lessons it settled`() = runTest {
        seedClass(now.minus(7200.seconds))
        seedClass(now.minus(3600.seconds))
        seedClass(now.plus(3600.seconds)) // still to come, so not settled by this pass
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals(2, maintenance().run(now).lessons)
    }

    @Test
    fun `a group lesson counts once - however many it billed`() = runTest {
        val subjectId = env.fixtures.subject("Математика")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.student(it) }
        val groupId = env.fixtures.group(subjectId = subjectId, members = members.toSet())
        val ids = env.fixtures.lesson(subjectId, members, start = now.minus(7200.seconds), groupId = groupId)
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals(1, maintenance().run(now).lessons, "one finished hour")
        val session = env.classRepo.getSessionFor(ids.first())!!
        assertEquals(3, session.size, "billed to all three of them")
    }

    @Test
    fun `what a pass billed leaves out a member excused from the lesson`() = runTest {
        val subjectId = env.fixtures.subject("Математика")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.student(it) }
        val groupId = env.fixtures.group(subjectId = subjectId, members = members.toSet())
        val ids = env.fixtures.lesson(subjectId, members, start = now.minus(7200.seconds), groupId = groupId)
        env.fixtures.setAttendance(ids.last(), Attended.ABSENT)
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals(Money.ofMajor(60), maintenance().run(now).billed, "two of three at 30 for the hour")
    }

    @Test
    fun `a pass that settles nothing reports nothing`() = runTest {
        seedClass(now.minus(7200.seconds))
        assertEquals(0, maintenance().run(now).lessons)
    }

    @Test
    fun `the backlog is countable while auto-completion is still off`() = runTest {
        seedClass(now.minus(7200.seconds))
        seedClass(now.minus(3600.seconds))
        seedClass(now.plus(3600.seconds))

        assertEquals(2, maintenance().dueForCompletion(now))
        assertEquals(0, maintenance().run(now).lessons, "and counting it settled nothing")
    }

    @Test
    fun `a watched pass reports to the screen and not to the tray`() = runTest {
        seedClass(now.minus(7200.seconds))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        val settled = maintenance().run(now)

        assertEquals(1, settled.lessons)
        assertTrue(env.notifier.autoCompleted.isEmpty(), "nothing posted")
    }

    @Test
    fun `a pass plans the digests the tutor has switched on`() = runTest {
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
    fun `a pass plans nothing for a digest that is off`() = runTest {
        maintenance().run(now)

        assertEquals(
            Digests.Plan(agendaAt = null, unfinishedAt = null, debtsAt = null),
            env.digestScheduler.planned,
        )
    }

    @Test
    fun `the lead time comes from settings`() = runTest {
        val id = seedClass(now.plus((90 * 60).seconds))
        env.settingsRepo.update { it.copy(reminderLeadMinutes = 120) }

        maintenance().run(now)

        val planned = env.reminders.armed.single()
        assertEquals(id, planned.classId)
        assertTrue(planned.due, "its lead moment has already passed")
    }

    @Test
    fun `a day ahead reminder is armed exactly 24 hours before the lesson`() = runTest {
        val start = now + 2.days
        val id = seedClass(start)
        env.settingsRepo.update { it.copy(reminderLeadMinutes = 24 * 60) }

        maintenance().run(now)

        assertEquals(Reminders.Plan(id, start - 1.days), env.reminders.armed.single())
    }

    @Test
    fun `a custom lead longer than a week is armed before it comes due`() = runTest {
        val id = seedClass(now + 11.days)
        env.settingsRepo.update { it.copy(reminderLeadMinutes = 10 * 24 * 60) }

        maintenance().run(now)

        assertEquals(Reminders.Plan(id, now + 1.days), env.reminders.armed.single())
    }
}
