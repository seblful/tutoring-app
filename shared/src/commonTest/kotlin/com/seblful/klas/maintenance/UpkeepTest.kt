package com.seblful.klas.maintenance

import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.maintenance.Upkeep.Trigger
import com.seblful.klas.testutil.RecordingNotifier
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class UpkeepTest {

    private val env = TestEnv()
    private val upkeep = env.upkeep
    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")

    @AfterTest
    fun close() = env.close()

    private suspend fun seedClass(start: Instant): Long {
        val subjectId = env.subjectRepo.createOrReuse("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        return env.fixtures.lesson(subjectId = subjectId, students = listOf(studentId), start = start).single()
    }

    private suspend fun pickFolder() = env.settingsRepo.update { it.copy(autoBackupFolder = FOLDER) }

    private fun copies() = env.backupFolder.files[FOLDER].orEmpty().keys

    @Test
    fun `a pass nobody is watching says what it billed`() = runTest {
        seedClass(now.minus(7200.seconds))
        seedClass(now.minus(3600.seconds))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        upkeep.on(Trigger.PERIODIC, now)

        assertEquals(
            listOf(RecordingNotifier.AutoCompleted(2, Money.ofMajor(60), LocalDate(2026, 6, 15))),
            env.notifier.autoCompleted,
        )
    }

    @Test
    fun `a pass nobody is watching names the latest day it settled`() = runTest {
        seedClass(now.minus(3.days))
        seedClass(now.minus(1.days))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        upkeep.on(Trigger.BOOT, now)

        assertEquals(LocalDate(2026, 6, 14), env.notifier.autoCompleted.single().day)
    }

    @Test
    fun `a pass that settles nothing stays quiet`() = runTest {
        seedClass(now.minus(7200.seconds))

        upkeep.on(Trigger.CLOCK_CHANGED, now)

        assertTrue(env.notifier.autoCompleted.isEmpty())
    }

    @Test
    fun `opening the app settles finished lessons without a word to the tray`() = runTest {
        val id = seedClass(now.minus(7200.seconds))
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        val settled = upkeep.on(Trigger.OPENED, now)

        assertEquals(1, settled.lessons)
        assertEquals(LessonStatus.COMPLETED, env.classRepo.getById(id)?.status)
        assertTrue(env.notifier.autoCompleted.isEmpty())
    }

    @Test
    fun `opening the app makes the weekly copy when it is due`() = runTest {
        pickFolder()

        upkeep.on(Trigger.OPENED)

        assertEquals(1, copies().size, "iOS has no background job, so opening is when the copy is made")
    }

    @Test
    fun `the periodic tick and a reboot make the weekly copy too`() = runTest {
        pickFolder()
        upkeep.on(Trigger.BOOT)
        assertEquals(1, copies().size)

        env.clock.advanceBy(AutoBackup.EVERY.inWholeMilliseconds)
        upkeep.on(Trigger.PERIODIC)

        assertEquals(2, copies().size)
    }

    @Test
    fun `a pass behind a write leaves the weekly copy alone`() = runTest {
        pickFolder()

        upkeep.on(Trigger.DATA_CHANGED)
        upkeep.on(Trigger.CLOCK_CHANGED)

        assertTrue(copies().isEmpty())
    }

    private companion object {
        const val FOLDER = "content://tree/backups"
    }
}
