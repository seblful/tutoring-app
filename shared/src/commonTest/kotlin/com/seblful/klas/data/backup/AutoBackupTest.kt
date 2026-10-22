package com.seblful.klas.data.backup

import com.seblful.klas.data.backup.AutoBackup.Outcome
import com.seblful.klas.testutil.FakeBackupFolder
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class AutoBackupTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }
    val env = TestEnv()
    private val folders = FakeBackupFolder()
    private val auto = AutoBackup(env.backupManager, env.settingsRepo, folders, env.clock)

    private suspend fun pick(folder: String = FOLDER) = env.settingsRepo.update { it.copy(autoBackupFolder = folder) }

    private fun advance(by: Duration) = env.clock.advanceBy(by.inWholeMilliseconds)

    private fun saved(folder: String = FOLDER) = folders.files[folder].orEmpty().keys.sorted()

    @Test
    fun `nothing is written until a folder is picked`() = runTest {
        assertEquals(Outcome.OFF, auto.runIfDue())
        assertTrue(folders.files.isEmpty())
    }

    @Test
    fun `the first copy goes out at once and is named for its day`() = runTest {
        env.fixtures.student(name = "Аня")
        pick()

        assertEquals(Outcome.SAVED, auto.runIfDue())

        assertEquals(listOf("klas-auto-backup-2026-06-01.json"), saved())
        assertTrue(folders.files.getValue(FOLDER).values.single().contains("Аня"))
        assertEquals(env.clock.now(), env.settingsRepo.get().lastExportAt)
    }

    @Test
    fun `a week passes between copies, and a manual export counts as the week's`() = runTest {
        pick()
        env.settingsRepo.update { it.copy(lastExportAt = env.clock.now()) }

        advance(6.days)
        assertEquals(Outcome.NOT_DUE, auto.runIfDue())

        advance(1.days - 1.hours)
        assertEquals(Outcome.SAVED, auto.runIfDue(), "a daily check a little early still counts")
        assertEquals(1, saved().size)
    }

    @Test
    fun `only the newest copies are kept, and files saved by hand are never touched`() = runTest {
        pick()
        folders.files[FOLDER] = mutableMapOf("klas-backup-2026-05-01.json" to "{}", "notes.txt" to "")

        repeat(AutoBackup.KEEP + 2) {
            assertEquals(Outcome.SAVED, auto.runIfDue())
            advance(7.days)
        }

        val ours = saved().filter { it.startsWith("klas-auto-backup-") }
        assertEquals(AutoBackup.KEEP, ours.size)
        assertEquals("klas-auto-backup-2026-06-15.json", ours.first())
        assertTrue("klas-backup-2026-05-01.json" in saved())
        assertTrue("notes.txt" in saved())
    }

    @Test
    fun `a folder that can no longer be reached is flagged, and a later copy clears it`() = runTest {
        pick()
        folders.revoked += FOLDER

        assertEquals(Outcome.FAILED, auto.runIfDue())
        assertTrue(env.settingsRepo.get().autoBackupFailed)
        assertNull(env.settingsRepo.get().lastExportAt, "a failed copy is not a backup")

        folders.revoked -= FOLDER
        assertEquals(Outcome.SAVED, auto.runIfDue())
        assertFalse(env.settingsRepo.get().autoBackupFailed)
    }

    @Test
    fun `a failure in a folder since replaced does not flag the new one`() = runTest {
        pick("old")
        folders.revoked += "old"
        val racing = AutoBackup(
            env.backupManager,
            env.settingsRepo,
            object : BackupFolder by folders {
                override suspend fun write(folder: String, name: String, text: String) {
                    env.settingsRepo.update { it.copy(autoBackupFolder = FOLDER) }
                    folders.write(folder, name, text)
                }
            },
            env.clock,
        )

        assertEquals(Outcome.FAILED, racing.runIfDue())

        assertFalse(env.settingsRepo.get().autoBackupFailed)
        assertEquals(FOLDER, env.settingsRepo.get().autoBackupFolder)
    }

    private companion object {
        const val FOLDER = "content://tree/backups"
    }
}
