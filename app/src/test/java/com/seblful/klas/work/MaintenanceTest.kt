package com.seblful.klas.work

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The upkeep pass, driven without WorkManager. Before [Maintenance] existed these decisions sat in
 * `MaintenanceWorker.doWork` and could only run on a device, so none of them had a test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MaintenanceTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()
    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")

    /** The environment's own — the same instance the settings screen's switch runs. */
    private fun maintenance() = env.maintenance

    /**
     * One lesson, booked through the repository — so its price and charge are what the app would have
     * written, not what this test remembered to fill in. It used to set `priceMinor` and omit
     * `chargeMinor`, one of three disagreeing hand-built versions of the same row (see [Fixtures]).
     */
    private suspend fun seedClass(start: Instant, status: LessonStatus = LessonStatus.SCHEDULED): Long {
        val subjectId = env.fixtures.subject("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        return env.fixtures.lesson(subjectId = subjectId, students = listOf(studentId), start = start)
            .single()
            .also { if (status != LessonStatus.SCHEDULED) env.fixtures.setStatus(it, status) }
    }

    @Test
    fun `auto-completes a finished class when the setting is on`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minusSeconds(7200)) // ended an hour ago
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().run(now)

        assertEquals(LessonStatus.COMPLETED, env.classRepo.getById(id)?.status)
    }

    @Test
    fun `leaves a finished class alone when auto-complete is off`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minusSeconds(7200))
        // Off is the default; state it anyway, since this is the whole point of the test.
        env.settingsRepo.update { it.copy(autoCompleteEnabled = false) }

        maintenance().run(now)

        assertEquals(LessonStatus.SCHEDULED, env.classRepo.getById(id)?.status)
    }

    @Test
    fun `arms a reminder for an upcoming class`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.plusSeconds(4 * 3600)) // in four hours, well past the 60-min lead

        maintenance().run(now)

        assertEquals(listOf(id), env.reminders.armed.map { it.classId })
    }

    @Test
    fun `re-planning replaces the previous set of reminders rather than adding to it`() =
        runTest(mainRule.dispatcher) {
            seedClass(now.plusSeconds(4 * 3600))

            val maintenance = maintenance()
            maintenance.run(now)
            maintenance.run(now)

            assertEquals("two passes, two hand-overs", 2, env.reminders.schedules.size)
            assertEquals("but only one reminder in force", 1, env.reminders.armed.size)
        }

    @Test
    fun `a class auto-completed in this same pass is not then given a reminder`() =
        runTest(mainRule.dispatcher) {
            // Ends in the past, so it auto-completes; a completed lesson is never announced. This
            // pins the ordering inside run(): complete first, then derive the plan.
            seedClass(now.minusSeconds(7200))
            env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

            maintenance().run(now)

            assertTrue(env.reminders.armed.isEmpty())
        }

    /**
     * The horizon is where the plan stops, and it is now also where the **query** stops: `run` asks
     * the repository for the classes starting within it instead of reading every class ever taught.
     *
     * The two bounds disagree by one millisecond on purpose — `ClassDao.getBetween` excludes its upper
     * bound while `Reminders.plan` keeps a lesson starting exactly on the horizon — so this is the case
     * that fails if the compensation in `ClassRepository.startingWithin` is ever tidied away. It fails
     * the quiet way, too: the lesson simply never gets a reminder, which is indistinguishable from
     * there being no lesson.
     */
    @Test
    fun `a lesson exactly on the horizon still earns a reminder`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.plus(Reminders.DEFAULT_HORIZON))

        maintenance().run(now)

        assertEquals(listOf(id), env.reminders.armed.map { it.classId })
    }

    @Test
    fun `a lesson past the horizon earns none`() = runTest(mainRule.dispatcher) {
        seedClass(now.plus(Reminders.DEFAULT_HORIZON).plusMillis(1))

        maintenance().run(now)

        assertTrue(env.reminders.armed.isEmpty())
    }

    // --- What a pass did, and what one would do -----------------------------------------------------
    //
    // Both counts exist for the settings switch: it asks the tutor about the backlog before turning
    // auto-completion on, and reports what it settled afterwards. Both are in **lessons**, because that
    // is what the tutor is being asked about — an evening's group lesson is one hour to confirm.

    @Test
    fun `a pass reports how many lessons it settled`() = runTest(mainRule.dispatcher) {
        seedClass(now.minusSeconds(7200))
        seedClass(now.minusSeconds(3600))
        seedClass(now.plusSeconds(3600)) // still to come, so not settled by this pass
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals(2, maintenance().run(now).lessons)
    }

    /**
     * The count the switch reports is lessons, not billed classes. A group of three finishing an hour
     * is **one** «Завершено 1 занятие» — reporting three would name a number the tutor cannot find
     * anywhere in their schedule.
     */
    @Test
    fun `a group lesson counts once, however many it billed`() = runTest(mainRule.dispatcher) {
        val subjectId = env.fixtures.subject("Математика")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.student(it) }
        val groupId = env.fixtures.group(subjectId = subjectId, members = members.toSet())
        val ids = env.fixtures.lesson(subjectId, members, start = now.minusSeconds(7200), groupId = groupId)
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals("one finished hour", 1, maintenance().run(now).lessons)
        val session = env.classRepo.getSessionFor(ids.first())!!
        assertEquals("billed to all three of them", 3, session.size)
    }

    /**
     * What a pass reports as billed is what it **billed** (`Billing.charged`), not every charge it
     * touched: a member excused before the hour was over is completed with the rest and owes nothing.
     */
    @Test
    fun `what a pass billed leaves out a member excused from the lesson`() = runTest(mainRule.dispatcher) {
        val subjectId = env.fixtures.subject("Математика")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.student(it) }
        val groupId = env.fixtures.group(subjectId = subjectId, members = members.toSet())
        val ids = env.fixtures.lesson(subjectId, members, start = now.minusSeconds(7200), groupId = groupId)
        env.fixtures.setAttendance(ids.last(), Attended.ABSENT)
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        assertEquals("two of three at 30 for the hour", Money.ofMajor(60), maintenance().run(now).billed)
    }

    @Test
    fun `a pass that settles nothing reports nothing`() = runTest(mainRule.dispatcher) {
        seedClass(now.minusSeconds(7200))
        // The setting is off, so the finished lesson is left for the tutor to confirm by hand.
        assertEquals(0, maintenance().run(now).lessons)
    }

    /**
     * The backlog is counted **regardless of the setting** — that is the whole point of it. It is what
     * the settings screen puts to the tutor *before* switching auto-completion on, at which moment the
     * setting is by definition still off.
     */
    @Test
    fun `the backlog is countable while auto-completion is still off`() = runTest(mainRule.dispatcher) {
        seedClass(now.minusSeconds(7200))
        seedClass(now.minusSeconds(3600))
        seedClass(now.plusSeconds(3600))

        assertEquals(2, maintenance().dueForCompletion(now))
        assertEquals("and counting it settled nothing", 0, maintenance().run(now).lessons)
    }

    // --- Who the pass reports to --------------------------------------------------------------------
    //
    // Completing a lesson **bills** it, so a pass that settles anything has moved the tutor's income.
    // Which pass says so is decided by who asked for it: the switch reports through the screen the tutor
    // is standing in front of, and the pass that runs at four in the afternoon with the app closed had
    // nothing to report to at all — so it recognised income in silence.

    @Test
    fun `an unwatched pass says what it billed`() = runTest(mainRule.dispatcher) {
        seedClass(now.minusSeconds(7200))
        seedClass(now.minusSeconds(3600))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().runUnwatched(now)

        // Two hours at 30, and the count is in lessons; 12:00Z is 15:00 in Minsk, the same day.
        assertEquals(
            listOf(RecordingNotifier.AutoCompleted(2, Money.ofMajor(60), LocalDate.of(2026, 6, 15))),
            env.notifier.autoCompleted,
        )
    }

    /**
     * The report opens the schedule on a day, and a backlog spans several — a phone that was off since
     * Friday settles Friday's lessons and Sunday's in one pass. The latest is the one the tutor still
     * remembers teaching, and the one a tap should show.
     */
    @Test
    fun `an unwatched pass names the latest day it settled`() = runTest(mainRule.dispatcher) {
        seedClass(now.minus(Duration.ofDays(3)))
        seedClass(now.minus(Duration.ofDays(1)))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().runUnwatched(now)

        assertEquals(LocalDate.of(2026, 6, 14), env.notifier.autoCompleted.single().day)
    }

    /** Nothing settled, nothing to announce — a notification whose content is "no news". */
    @Test
    fun `an unwatched pass that settles nothing stays quiet`() = runTest(mainRule.dispatcher) {
        seedClass(now.minusSeconds(7200))
        // The setting is off, so nothing is completed and nothing was billed.
        maintenance().runUnwatched(now)

        assertTrue(env.notifier.autoCompleted.isEmpty())
    }

    /**
     * The pass the tutor **asked for** must not also post. It reports through `SettingsViewModel.swept`,
     * in the snackbar under the switch they just flipped; announcing it in the tray as well would be the
     * app telling them something they had just been told.
     */
    @Test
    fun `a watched pass reports to the screen and not to the tray`() = runTest(mainRule.dispatcher) {
        seedClass(now.minusSeconds(7200))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        val settled = maintenance().run(now)

        assertEquals(1, settled.lessons)
        assertTrue("nothing posted", env.notifier.autoCompleted.isEmpty())
    }

    // --- The digests are re-planned by the same pass -------------------------------------------------

    /**
     * Every trigger that re-derives the reminder plan re-plans the digests too, from the same settings
     * read: an app start, a reboot, a periodic tick, a settings change. Otherwise a digest whose hour was
     * changed while the app was closed would keep its old one until something happened to touch it.
     */
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
        // `now` is Monday 15 June 2026, 12:00 UTC — 15:00 in Europe/Minsk, so nine has gone for today and
        // for this Monday. Both roll forward rather than firing immediately.
        assertEquals(Instant.parse("2026-06-16T06:00:00Z"), planned.agendaAt)
        assertEquals(Instant.parse("2026-06-22T06:00:00Z"), planned.debtsAt)
    }

    /** A digest switched off is planned for nothing, which is what cancels work already enqueued. */
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
        // 90 minutes ahead: inside a 120-minute lead (so it is already too late to announce) but
        // outside the default 60-minute one.
        seedClass(now.plusSeconds(90 * 60))
        env.settingsRepo.update { it.copy(reminderLeadMinutes = 120) }

        maintenance().run(now)

        assertTrue("its lead moment has already passed", env.reminders.armed.isEmpty())
    }
}
