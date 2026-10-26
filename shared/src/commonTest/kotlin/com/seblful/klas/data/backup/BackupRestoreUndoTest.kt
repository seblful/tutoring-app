package com.seblful.klas.data.backup

import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class BackupRestoreUndoTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }
    val env = TestEnv()

    private suspend fun names() = env.studentRepo.observeAll().first().map { it.name }.sorted()

    /** A backup of one student, made on another phone. */
    private suspend fun backupOf(name: String): String = TestEnv().use { other ->
        other.fixtures.pricedStudent(name, subjectId = other.fixtures.subject())
        other.backupManager.exportJson()
    }

    @Test
    fun `undoing a restore puts back what was on the phone and forgets the copy`() = runTest {
        val file = backupOf("Аня")
        env.fixtures.pricedStudent("Борис", subjectId = env.fixtures.subject("Физика"))

        env.backupRestore.restore(file)
        assertEquals(listOf("Аня"), names())
        assertEquals(env.clock.now(), env.backupRestore.undoableSince.value)

        assertTrue(env.backupRestore.undoRestore())
        assertEquals(listOf("Борис"), names())
        assertNull(env.backupRestore.undoableSince.value)
        assertFalse(env.backupRestore.undoRestore())
    }

    @Test
    fun `a restore whose copy could not be kept does not leave the last restore's copy on offer`() = runTest {
        env.fixtures.pricedStudent("Борис", subjectId = env.fixtures.subject("Физика"))
        env.backupRestore.restore(backupOf("Аня")) // keeps a copy of Борис
        assertEquals(listOf("Аня"), names())
        val diskFull = object : RestoreSnapshots by env.restoreSnapshots {
            override suspend fun save(json: String, takenAt: Instant) = error("disk full")
        }
        val restore = BackupRestore(
            env.backupManager, env.settingsRepo, env.calendarMirror, env.reminders, diskFull, env.clock,
        )

        restore.restore(backupOf("Вера"))

        assertEquals(listOf("Вера"), names())
        assertNull(restore.undoableSince.value, "the copy on offer would put Борис back over Вера")
        assertFalse(restore.undoRestore())
    }

    @Test
    fun `a restore onto an empty phone keeps nothing to undo`() = runTest {
        val file = backupOf("Аня")

        env.backupRestore.restore(file)

        assertNull(env.backupRestore.undoableSince.value)
        assertFalse(env.backupRestore.undoRestore())
        assertEquals(listOf("Аня"), names())
    }

    @Test
    fun `a refused file leaves the copy from the last restore as it was`() = runTest {
        val file = backupOf("Аня")
        env.fixtures.pricedStudent("Борис", subjectId = env.fixtures.subject("Физика"))
        env.backupRestore.restore(file)
        val kept = env.restoreSnapshots.kept

        assertFailsWith<Exception> { env.backupRestore.restore("not a backup") }

        assertEquals(kept, env.restoreSnapshots.kept)
        assertTrue(env.backupRestore.undoRestore())
        assertEquals(listOf("Борис"), names())
    }

    @Test
    fun `the preview counts what a file holds and says when it was made`() = runTest {
        val subject = env.fixtures.subject()
        val anya = env.fixtures.pricedStudent("Аня", subjectId = subject)
        env.fixtures.pricedStudent("Борис", subjectId = subject)
        env.fixtures.group(subjectId = subject, members = setOf(anya))
        env.fixtures.lesson(subject, listOf(anya), Instant.parse("2026-06-20T10:00:00Z"))
        env.paymentRepo.record(anya, Money.ofMajor(30), LocalDate(2026, 6, 20))
        val at = Instant.parse("2026-06-21T08:00:00Z")

        val preview = env.backupManager.preview(env.backupManager.exportJson(at))

        assertEquals(
            BackupPreview(exportedAt = at, students = 2, groups = 1, lessons = 1, payments = 1, homework = 0),
            preview,
        )
        assertNull(env.backupManager.preview(env.backupManager.exportJson()).exportedAt)
    }

    @Test
    fun `the preview refuses what a restore would refuse`() = runTest {
        assertFailsWith<Exception> { env.backupManager.preview("not a backup") }
    }
}
