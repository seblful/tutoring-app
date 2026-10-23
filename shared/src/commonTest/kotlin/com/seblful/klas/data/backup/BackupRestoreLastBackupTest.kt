package com.seblful.klas.data.backup

import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** A restored file is a backup of what is now on the phone, so «No backup yet» is not said after one. */
class BackupRestoreLastBackupTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }
    val env = TestEnv()

    /** A backup of one student, made on another phone at [at]. */
    private suspend fun backupMadeAt(at: Instant?): String = TestEnv().use { other ->
        other.fixtures.pricedStudent("Аня", subjectId = other.fixtures.subject())
        other.backupManager.exportJson(at)
    }

    private suspend fun lastBackup() = env.settingsRepo.get().lastExportAt

    @Test
    fun `a restored file is the last backup - on the date it was made`() = runTest {
        val made = Instant.parse("2026-10-01T09:00:00Z")

        env.backupRestore.restore(backupMadeAt(made))

        assertEquals(made, lastBackup())
    }

    @Test
    fun `a file too old to carry its date counts from the restore`() = runTest {
        env.backupRestore.restore(backupMadeAt(null))

        assertEquals(env.clock.now(), lastBackup())
    }

    @Test
    fun `an older file does not take back a later backup`() = runTest {
        val later = env.clock.now()
        env.settingsRepo.update { it.copy(lastExportAt = later) }

        env.backupRestore.restore(backupMadeAt(Instant.parse("2026-01-01T00:00:00Z")))

        assertEquals(later, lastBackup())
    }

    @Test
    fun `undoing a restore keeps the backup the restore counted`() = runTest {
        val made = Instant.parse("2026-10-01T09:00:00Z")
        env.fixtures.pricedStudent("Борис", subjectId = env.fixtures.subject("Физика"))
        env.backupRestore.restore(backupMadeAt(made))

        env.backupRestore.undoRestore()

        assertEquals(made, lastBackup())
    }
}
