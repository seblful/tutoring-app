package com.seblful.klas.data.backup

import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BackupRestoreCalendarTest {
    @get:Rule
    val env = TestEnv()

    @Test
    fun `restoring on the same device retires its old mirrors before creating fresh events`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), Instant.parse("2026-06-20T10:00:00Z")).single()
        val oldEvent = env.classRepo.getById(id)!!.calendarEventId!!

        env.backupRestore.restore(env.backupManager.exportJson())

        assertEquals(listOf(oldEvent), env.calendar.deletes)
        assertNotEquals(oldEvent, env.classRepo.getById(id)!!.calendarEventId)
    }

    @Test
    fun `a rejected file cannot withdraw existing calendar events`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), Instant.parse("2026-06-20T10:00:00Z")).single()
        val oldEvent = env.classRepo.getById(id)!!.calendarEventId

        assertTrue(runCatching { env.backupRestore.restore("not a backup") }.isFailure)

        assertTrue(env.calendar.deletes.isEmpty())
        assertEquals(oldEvent, env.classRepo.getById(id)!!.calendarEventId)
    }
}
