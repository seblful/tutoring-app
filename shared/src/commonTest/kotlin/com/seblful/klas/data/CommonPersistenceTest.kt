package com.seblful.klas.data

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.backup.BackupData
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.settings.DevicePreferences
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

internal expect fun openTestDatabase(): AppDatabase

class CommonPersistenceTest {
    @Test
    fun backupRoundTripPreservesBookingsAndPaymentsButKeepsDeviceSettings() = runTest {
        val first = Fixture()
        val second = Fixture()
        try {
            first.seed()
            assertIs<LessonWrite.Written<*>>(first.book(listOf(1)))
            val payments = PaymentRepository(first.db.paymentDao(), WallClock.fixed(START, TimeZone.UTC))
            payments.record(1, Money.ofMajor(25), LocalDate(2026, 10, 20))
            first.settings.update { it.copy(currency = Currency.USD) }

            val exported = first.backup.exportJson()
            second.backup.importJson(exported)

            // The rows, which an import replaces; the settings a file carries are the restore's to apply
            // (`BackupPreferencesTest`), so each phone's export still speaks for its own.
            val original = first.backup.export()
            val restored = second.backup.export()
            assertEquals(
                original.copy(
                    attendance = original.attendance.map {
                        it.copy(calendarEventId = null)
                    },
                    preferences = null,
                ),
                restored.copy(preferences = null),
            )
            assertEquals("USD", original.preferences?.currency)
            assertTrue(restored.attendance.all { it.calendarEventId == null })
            assertEquals(Money.ofMajor(25), second.db.paymentDao().getAll().single().toDomain().amount)
            assertEquals(Currency.BYN, second.settings.get().currency)
        } finally {
            first.db.close()
            second.db.close()
        }
    }

    @Test
    fun conflictingBookingLeavesRowsAndEffectsUnchanged() = runTest {
        val fixture = Fixture()
        try {
            fixture.seed()
            fixture.book(listOf(1))
            val before = fixture.backup.export()

            assertIs<LessonWrite.HourTaken>(fixture.book(listOf(1)))

            assertEquals(before, fixture.backup.export())
            assertEquals(1, fixture.refreshes)
        } finally {
            fixture.db.close()
        }
    }

    @Test
    fun failedAttendanceWriteRollsBackTheWholeLessonBeforeAnyEffects() = runTest {
        val fixture = Fixture()
        try {
            fixture.seed()
            assertFailsWith<Exception> { fixture.book(listOf(1, 999)) }

            val stored = fixture.backup.export()
            assertTrue(stored.lessons.isEmpty())
            assertTrue(stored.attendance.isEmpty())
            assertEquals(0, fixture.refreshes)
            assertEquals(0, fixture.mirrored)
        } finally {
            fixture.db.close()
        }
    }

    private class Fixture {
        val db = openTestDatabase()
        private val transactor = RoomTransactor(db)
        val settings = SettingsRepository(db.settingsDao(), DevicePreferences(listOf("ru-BY"), listOf("BYN")))
        val backup = BackupManager(transactor, db.snapshotDao(), settings)
        var refreshes = 0
        var mirrored = 0
        private val reminders = object : ReminderScheduler {
            override fun refreshNow() { refreshes++ }
            override fun ensurePeriodicMaintenance() = Unit
            override suspend fun scheduleNextPass(at: Instant?) = Unit
            override fun schedule(plans: List<Reminders.Plan>) = Unit
        }
        private val calendar = object : CalendarSync {
            override suspend fun upsert(
                klass: TutoringClass,
                title: String,
                guest: Guest?,
                reminderLeadMinutes: Int,
                description: String,
            ): String { mirrored++; return "event-${klass.id}" }

            override suspend fun refresh(klass: TutoringClass, title: String, description: String) = Unit
            override suspend fun delete(eventId: String) = Unit
        }
        private val mirror = CalendarMirror(
            db.classDao(), db.attendanceDao(), db.studentDao(), db.subjectDao(), db.groupDao(),
            calendar, settings, db.homeworkDao(), transactor, WallClock.system(),
        )
        private val classes = ClassRepository(
            db.classDao(), db.lessonDao(), db.attendanceDao(), mirror, reminders, transactor,
        )

        suspend fun seed() {
            backup.import(
                BackupData(
                    subjects = listOf(SubjectEntity(1, "Math")),
                    students = listOf(StudentEntity(1, "Tutor's student")),
                ),
            )
            settings.update { it.copy(calendarSyncEnabled = true) }
        }

        suspend fun book(students: List<Long>) = classes.book(
            subjectId = 1,
            start = START,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = students,
            price = Money.ofMajor(30),
        )
    }

    private companion object {
        val START = Instant.parse("2026-10-20T12:00:00Z")
    }
}
