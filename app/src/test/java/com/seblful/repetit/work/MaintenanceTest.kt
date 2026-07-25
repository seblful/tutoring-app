package com.seblful.repetit.work

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.testutil.MainDispatcherRule
import com.seblful.repetit.testutil.TestEnv
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

    private suspend fun seedStudentAndSubject(): Pair<Long, Long> {
        val studentId = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subjectId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        return studentId to subjectId
    }

    private suspend fun seedClass(start: Instant, status: String = "SCHEDULED"): Long {
        val (studentId, subjectId) = seedStudentAndSubject()
        return env.classDao.upsert(
            ClassEntity(
                studentId = studentId,
                subjectId = subjectId,
                startMillis = start.toEpochMilli(),
                durationMinutes = 60,
                priceMinor = Money.ofMajor(30).minor,
                status = status,
            ),
        )
    }

    @Test
    fun `auto-completes a finished class when the setting is on`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minusSeconds(7200)) // ended an hour ago
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        maintenance().run(now)

        assertEquals("COMPLETED", env.classDao.items.value.single { it.id == id }.status)
    }

    @Test
    fun `leaves a finished class alone when auto-complete is off`() = runTest(mainRule.dispatcher) {
        val id = seedClass(now.minusSeconds(7200))
        // Off is the default; state it anyway, since this is the whole point of the test.
        env.settingsRepo.update { it.copy(autoCompleteEnabled = false) }

        maintenance().run(now)

        assertEquals("SCHEDULED", env.classDao.items.value.single { it.id == id }.status)
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
