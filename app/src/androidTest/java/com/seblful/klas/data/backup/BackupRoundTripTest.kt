package com.seblful.klas.data.backup

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager

    private fun newDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        )
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun managerFor(db: AppDatabase) = BackupManager(RoomTransactor(db), db.snapshotDao())

    @Before fun setUp() {
        db = newDb()
        manager = managerFor(db)
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun exportThenImportOnEmptyDbReproducesIdenticalData() = runTest {
        db.snapshotDao().insert(
            DatabaseSnapshot(
                subjects = listOf(SubjectEntity(1, "Математика"), SubjectEntity(2, "Физика")),
                students = listOf(
                    StudentEntity(1, "Иван", phone = "+375291112233", note = "ОГЭ", defaultMode = ClassMode.REMOTE),
                    StudentEntity(2, "Мария", archived = true),
                ),
                studentSubjects = listOf(
                    StudentSubjectCrossRef(1, 1), StudentSubjectCrossRef(1, 2), StudentSubjectCrossRef(2, 1),
                ),
                groups = listOf(GroupEntity(7, "11 «А»", note = "вечер", defaultMode = ClassMode.REMOTE)),
                groupMembers = listOf(GroupMemberCrossRef(7, 1), GroupMemberCrossRef(7, 2)),
                prices = listOf(PriceEntity(1, 1, 5000), PriceEntity(2, 1, 4550)),
                rateHistory = listOf(
                    RateChangeEntity(1, studentId = 1, subjectId = 1, toMinor = 4000, atMillis = 1_890_000_000_000),
                    RateChangeEntity(
                        2, studentId = 1, subjectId = 1, fromMinor = 4000, toMinor = 5000,
                        atMillis = 1_895_000_000_000,
                    ),
                    RateChangeEntity(3, groupId = 7, toMinor = 2000, atMillis = null),
                ),
                lessons = listOf(
                    LessonEntity(
                        1, 1, 1_900_000_000_000, 90, LessonStatus.COMPLETED, groupId = 7, mode = ClassMode.REMOTE,
                    ),
                    LessonEntity(2, 1, 1_900_100_000_000, 60, LessonStatus.SCHEDULED),
                ),
                attendance = listOf(
                    AttendanceEntity(
                        1, 1, 1, Attended.PRESENT, priceMinor = 5000, chargeMinor = 5000, calendarEventId = "evt-1",
                    ),
                    AttendanceEntity(2, 1, 2, Attended.ABSENT, priceMinor = 5000),
                    AttendanceEntity(3, 2, 2, Attended.EXPECTED),
                ),
                payments = listOf(PaymentEntity(1, 1, 1_899_000_000_000, 3000)),
            ),
        )

        val original = manager.export()
        val jsonText = manager.exportJson()

        val emptyDb = newDb()
        val reproduced = try {
            val emptyManager = managerFor(emptyDb)
            emptyManager.importJson(jsonText)
            emptyManager.export()
        } finally {
            emptyDb.close()
        }

        val expected = original.copy(attendance = original.attendance.map { it.copy(calendarEventId = null) })
        assertEquals(expected, reproduced)
    }
}
