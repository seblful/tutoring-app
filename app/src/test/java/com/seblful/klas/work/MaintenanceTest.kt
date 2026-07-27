package com.seblful.klas.work

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.Reminders
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The upkeep pass, driven without WorkManager. Before [Maintenance] existed these decisions sat in
 * `MaintenanceWorker.doWork` and could only run on a device, so none of them had a test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()
    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")

    private fun maintenance() = Maintenance(
        classes = env.classRepo,
        settings = env.settingsRepo,
        scheduler = env.reminders,
    )

    /**
     * One lesson, booked through the repository — so its price and charge are what the app would have
     * written, not what this test remembered to fill in. It used to set `priceMinor` and omit
     * `chargeMinor`, one of three disagreeing hand-built versions of the same row (see [Fixtures]).
     */
    private suspend fun seedClass(start: Instant, status: ClassStatus = ClassStatus.SCHEDULED): Long {
        val subjectId = env.fixtures.subject("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        return env.fixtures.lesson(subjectId = subjectId, students = listOf(studentId), start = start)
            .single()
            .also { if (status != ClassStatus.SCHEDULED) env.fixtures.setStatus(it, status) }
    }

    @Test
    fun `auto-completes a finished class when the setting is on`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minusSeconds(7200)) // ended an hour ago
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().run(now)

        assertEquals(ClassStatus.COMPLETED, env.classRepo.getById(id)?.status)
    }

    @Test
    fun `leaves a finished class alone when auto-complete is off`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minusSeconds(7200))
        // Off is the default; state it anyway, since this is the whole point of the test.
        env.settingsRepo.update { it.copy(autoCompleteEnabled = false) }

        maintenance().run(now)

        assertEquals(ClassStatus.SCHEDULED, env.classRepo.getById(id)?.status)
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
