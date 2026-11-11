package com.seblful.klas.data.backup

import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.guide.PhoneSetup
import com.seblful.klas.domain.guide.PhoneStep
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The settings that travel with a backup: what the rows mean comes with them, what the old phone had on is asked
 * for again on the new one, and the rest stays where it is.
 */
class BackupPreferencesTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }
    val env = TestEnv()

    private suspend fun settings() = env.settingsRepo.get()

    /** A backup of one student, from a phone set up as [setUp]. */
    private suspend fun backupFrom(setUp: (AppSettings) -> AppSettings): String = TestEnv().use { other ->
        other.settingsRepo.update(setUp)
        other.fixtures.pricedStudent("Аня", subjectId = other.fixtures.subject())
        other.backupManager.exportJson()
    }

    private val tutorsPhone: (AppSettings) -> AppSettings = {
        it.copy(
            currency = Currency.BYN,
            defaultLessonMinutes = 45,
            defaultGroupPrice = Money.ofMajor(25),
            autoCompleteEnabled = true,
            notificationsEnabled = true,
            calendarSyncEnabled = true,
            autoBackupFolder = "content://tree/klas",
        )
    }

    @Test
    fun `a restore brings what the amounts and bookings mean`() = runTest {
        env.settingsRepo.update { it.copy(currency = Currency.USD, defaultLessonMinutes = 60) }

        env.backupRestore.restore(backupFrom(tutorsPhone))

        val restored = settings()
        assertEquals(Currency.BYN, restored.currency)
        assertEquals(45, restored.defaultLessonMinutes)
        assertEquals(Money.ofMajor(25), restored.defaultGroupPrice)
        assertEquals(true, restored.autoCompleteEnabled)
    }

    @Test
    fun `what the old phone had on is asked for - not switched on`() = runTest {
        env.backupRestore.restore(backupFrom(tutorsPhone))

        val restored = settings()
        assertEquals(setOf(PhoneStep.REMINDERS, PhoneStep.CALENDAR, PhoneStep.WEEKLY_COPY), restored.phoneSetup)
        assertEquals(false, restored.calendarSyncEnabled)
        assertNull(restored.autoBackupFolder)
    }

    @Test
    fun `a phone that had nothing on asks for nothing`() = runTest {
        env.backupRestore.restore(
            backupFrom { it.copy(notificationsEnabled = false, calendarSyncEnabled = false, autoBackupFolder = null) },
        )

        assertEquals(emptySet(), settings().phoneSetup)
    }

    @Test
    fun `a file from before v11 leaves the settings and asks for reminders and the weekly copy`() = runTest {
        env.settingsRepo.update { it.copy(currency = Currency.EUR, defaultLessonMinutes = 90) }
        val old = env.backupManager.encode(env.backupManager.parse(backupFrom(tutorsPhone)).copy(preferences = null))

        env.backupRestore.restore(old)

        val restored = settings()
        assertEquals(Currency.EUR, restored.currency)
        assertEquals(90, restored.defaultLessonMinutes)
        assertEquals(PhoneSetup.UNKNOWN, restored.phoneSetup)
    }

    @Test
    fun `a currency this build does not know keeps this phone's`() = runTest {
        env.settingsRepo.update { it.copy(currency = Currency.EUR) }
        val file = env.backupManager.parse(backupFrom(tutorsPhone))
        val unknown = env.backupManager.encode(file.copy(preferences = file.preferences!!.copy(currency = "XTS")))

        env.backupRestore.restore(unknown)

        assertEquals(Currency.EUR, settings().currency)
        assertEquals(45, settings().defaultLessonMinutes)
    }

    @Test
    fun `undoing a restore puts this phone's settings back and keeps what it was asked for`() = runTest {
        env.settingsRepo.update { it.copy(currency = Currency.USD, defaultLessonMinutes = 60) }
        env.fixtures.pricedStudent("Борис", subjectId = env.fixtures.subject("Физика"))
        env.backupRestore.restore(backupFrom(tutorsPhone))

        env.backupRestore.undoRestore()

        val undone = settings()
        assertEquals(Currency.USD, undone.currency)
        assertEquals(60, undone.defaultLessonMinutes)
        assertEquals(setOf(PhoneStep.REMINDERS, PhoneStep.CALENDAR, PhoneStep.WEEKLY_COPY), undone.phoneSetup)
    }

    @Test
    fun `a backup carries that its tutor predates Pro`() = runTest {
        env.backupRestore.restore(backupFrom { tutorsPhone(it).copy(earlyUser = true) })

        assertEquals(true, settings().earlyUser)
    }

    @Test
    fun `a backup of a tutor who came after Pro grants nothing`() = runTest {
        env.backupRestore.restore(backupFrom(tutorsPhone))

        assertEquals(false, settings().earlyUser)
    }

    @Test
    fun `a file written before Pro existed is an early user's`() = runTest {
        val file = env.backupManager.parse(backupFrom(tutorsPhone))
        val beforePro = env.backupManager.encode(file.copy(preferences = file.preferences!!.copy(earlyUser = null)))

        env.backupRestore.restore(beforePro)

        assertEquals(true, settings().earlyUser)
    }

    @Test
    fun `restoring never takes early access away`() = runTest {
        env.settingsRepo.update { it.copy(earlyUser = true) }

        env.backupRestore.restore(backupFrom(tutorsPhone))
        env.backupRestore.undoRestore()

        assertEquals(true, settings().earlyUser)
    }
}
