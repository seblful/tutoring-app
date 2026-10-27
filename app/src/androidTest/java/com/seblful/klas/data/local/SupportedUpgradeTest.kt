package com.seblful.klas.data.local

import android.content.ContextWrapper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.HostServices
import com.seblful.klas.createKlasStore
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money
import com.seblful.klas.settings.DevicePreferences
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.InMemoryRestoreSnapshots
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingNotifier
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.testutil.testClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SupportedUpgradeTest {
    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test fun upgradeFrom5() = checkUpgrade(5)

    @Test fun upgradeFrom6() = checkUpgrade(6)

    @Test fun upgradeFrom7() = checkUpgrade(7)

    @Test fun upgradeFrom8() = checkUpgrade(8)

    @Test fun upgradeFrom9() = checkUpgrade(9)

    private fun checkUpgrade(version: Int) = runTest {
        helper.createDatabase(version).use { it.seed(version) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Exercise the factory that ships, with an isolated filename and destructive fallback off.
        val isolated = object : ContextWrapper(context) {
            override fun getDatabasePath(name: String): File = context.getDatabasePath(TEST_DB)
        }
        val host = object : HostServices {
            override val preferences = DevicePreferences(listOf("en-US"), listOf("USD"))
            override val clock = testClock()
            override val calendar = FakeCalendarSync()
            override val reminders = RecordingReminderScheduler()
            override val digests = RecordingDigestScheduler()
            override val notifier = RecordingNotifier()
            override val restoreSnapshots = InMemoryRestoreSnapshots()
        }
        openDatabase(isolated).use { database ->
            val store = createKlasStore(database, host)
            assertEquals("Ann", store.students.getById(1)?.name)
            assertTrue(store.students.getById(2)!!.archived)
            assertEquals(setOf(1L, 2L), store.groups.getMemberIds(7).toSet())
            assertEquals(Money(3000), store.enrollment.priceFor(1, 1)?.amount)
            val classes = store.classes.getAll().sortedBy { it.id }
            assertEquals(listOf(100L, 200L, 201L, 300L), classes.map { it.id })
            assertEquals(listOf(4500L, 2000L, 2000L, 0L), classes.map { it.charge.minor })
            assertEquals("local-event", classes.last().calendarEventId)
            assertEquals(2, store.classes.getSessionFor(200)!!.size)
            assertEquals(Money(1000), store.payments.getAll().single().amount)
            val ann = store.studentDetails.observe(1).first()
            assertEquals(Balance.Owed(Money(5500)), ann.balance)
            assertEquals(1, ann.subjects.single().history.size)
            val homework = store.homework.observeAll().first()
            assertEquals(
                if (version >= 8) listOf("Exercise 1", "Group exercise") else emptyList(),
                homework.map { it.text }.sorted(),
            )
            assertEquals(if (version >= 8) 2 else 0, homework.count { it.doneAt != null })
            assertEquals(90, store.settings.get().defaultLessonMinutes)
        }
        BundledSQLiteDriver().open(context.getDatabasePath(TEST_DB).absolutePath).use { connection ->
            assertEquals(emptyList<Map<String, Any?>>(), connection.rows("PRAGMA foreign_key_check"))
            assertEquals(Migrations.ALL.last().endVersion.toLong(), connection.long("PRAGMA user_version"))
        }
    }

    private fun SQLiteConnection.seed(version: Int) {
        execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Maths')")
        execSQL(
            "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES " +
                "(1, 'Ann', '', '', 'retained note', 'REMOTE', 0), (2, 'Bob', '', '', '', 'IN_PERSON', 1)",
        )
        execSQL("INSERT INTO student_subjects (studentId, subjectId) VALUES (1, 1), (2, 1)")
        execSQL("INSERT INTO prices (studentId, subjectId, amountMinor) VALUES (1, 1, 3000), (2, 1, 2000)")
        execSQL(
            "INSERT INTO student_groups (id, name, note, subjectId, priceMinor, defaultMode, archived) " +
                "VALUES (7, 'Group', 'retained', 1, 2000, 'IN_PERSON', 0)",
        )
        execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 1), (7, 2)")
        execSQL(
            "INSERT INTO price_history (id, studentId, subjectId, groupId, fromMinor, toMinor, atMillis) " +
                "VALUES (1, 1, 1, NULL, NULL, 3000, 1000)",
        )
        execSQL(
            "INSERT INTO lessons (id, subjectId, startMillis, durationMinutes, status, groupId, mode) VALUES " +
                "(10, 1, 1000000, 90, 'COMPLETED', NULL, 'REMOTE'), " +
                "(20, 1, 10000000, 60, 'COMPLETED', 7, 'IN_PERSON'), " +
                "(30, 1, 20000000, 60, 'SCHEDULED', NULL, 'REMOTE')",
        )
        execSQL(
            "INSERT INTO attendance (id, lessonId, studentId, state, priceMinor, chargeMinor, calendarEventId) " +
                "VALUES (100, 10, 1, 'PRESENT', 3000, 4500, NULL), " +
                "(200, 20, 1, 'PRESENT', 2000, 2000, NULL), " +
                "(201, 20, 2, 'ABSENT_BILLED', 2000, 2000, NULL), " +
                "(300, 30, 2, 'EXPECTED', 2000, 0, 'local-event')",
        )
        execSQL("INSERT INTO payments (id, studentId, dateMillis, amountMinor) VALUES (1, 1, 30000000, 1000)")
        execSQL(
            "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, emailInvitesEnabled, " +
                "onboardingComplete, reminderLeadMinutes, dailyAgendaEnabled, agendaMinuteOfDay, debtDigestEnabled, " +
                "debtDayOfWeek, debtMinuteOfDay, autoCompleteEnabled, themeMode, dynamicColorEnabled, " +
                "defaultLessonMinutes, defaultGroupPriceMinor, currency, showGroupsTab, lastExportMillis) " +
                "VALUES (0, 0, 0, 0, 1, 60, 0, 480, 0, 1, 540, 0, 'DARK', 0, 90, 2000, 'USD', 1, 30000000)",
        )
        if (version == 8) {
            execSQL(
                "UPDATE lessons SET homework = CASE id WHEN 10 THEN 'Exercise 1' ELSE 'Group exercise' END " +
                    "WHERE id IN (10, 20)",
            )
            execSQL("UPDATE attendance SET homeworkResult = 'DONE' WHERE lessonId IN (10, 20)")
        } else if (version >= 9) {
            execSQL(
                "INSERT INTO homework (id, studentId, groupId, text, givenAtMillis, doneAtMillis) VALUES " +
                    "(1, 1, NULL, 'Exercise 1', 1000000, 1000000), " +
                    "(2, NULL, 7, 'Group exercise', 10000000, 10000000)",
            )
        }
    }

    private companion object {
        const val TEST_DB = "supported-upgrade-klas.db"
    }
}
