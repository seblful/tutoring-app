package com.seblful.klas.data.backup

import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BackupRestoreCalendarTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }
    val env = TestEnv()

    @Test
    fun `restoring this device's own backup keeps the events it already mirrors`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), Instant.parse("2026-06-20T10:00:00Z")).single()
        val event = env.classRepo.getById(id)!!.calendarEventId!!

        env.backupRestore.restore(env.backupManager.exportJson())

        assertTrue(env.calendar.deletes.isEmpty())
        assertEquals(event, env.classRepo.getById(id)!!.calendarEventId)
    }

    @Test
    fun `a taught lesson keeps its event through a restore - though none could be created for it`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.completedLesson(subject, listOf(student), Instant.parse("2026-06-20T10:00:00Z")).single()
        val event = env.classRepo.getById(id)!!.calendarEventId
        assertNotNull(event)

        env.backupRestore.restore(env.backupManager.exportJson())

        assertTrue(env.calendar.deletes.isEmpty())
        assertEquals(event, env.classRepo.getById(id)!!.calendarEventId)
    }

    @Test
    fun `an event whose class the file describes differently is retired and mirrored afresh`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val start = Instant.parse("2026-06-20T10:00:00Z")
        val id = env.fixtures.lesson(subject, listOf(student), start).single()
        val backup = env.backupManager.exportJson()
        env.classRepo.move(env.classRepo.getSessionFor(id)!!, start.plus((3_600).seconds), start)
        val movedEvent = env.classRepo.getById(id)!!.calendarEventId!!

        env.backupRestore.restore(backup)

        assertEquals(listOf(movedEvent), env.calendar.deletes)
        assertNotEquals(movedEvent, env.classRepo.getById(id)!!.calendarEventId)
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
