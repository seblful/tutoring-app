package com.seblful.tutoringapp.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.tutoringapp.data.local.AppDatabase
import com.seblful.tutoringapp.data.local.ClassEntity
import com.seblful.tutoringapp.data.local.PaymentEntity
import com.seblful.tutoringapp.data.local.PriceEntity
import com.seblful.tutoringapp.data.local.StudentEntity
import com.seblful.tutoringapp.data.local.StudentSubjectCrossRef
import com.seblful.tutoringapp.data.local.SubjectEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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
        ).build()

    private fun managerFor(db: AppDatabase) = BackupManager(
        db, db.subjectDao(), db.studentDao(), db.priceDao(), db.classDao(), db.paymentDao(),
    )

    @Before fun setUp() {
        db = newDb()
        manager = managerFor(db)
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun exportThenImportOnEmptyDbReproducesIdenticalData() = runTest {
        // Seed the source database (parents before children for foreign keys).
        db.subjectDao().insertAll(listOf(SubjectEntity(1, "Математика"), SubjectEntity(2, "Физика")))
        db.studentDao().insertAll(
            listOf(
                StudentEntity(1, "Иван", phone = "+375291112233", note = "ОГЭ", defaultMode = "REMOTE"),
                StudentEntity(2, "Мария", archived = true),
            ),
        )
        db.studentDao().insertLinks(
            listOf(StudentSubjectCrossRef(1, 1), StudentSubjectCrossRef(1, 2), StudentSubjectCrossRef(2, 1)),
        )
        db.priceDao().insertAll(listOf(PriceEntity(1, 1, 5000), PriceEntity(2, 1, 4550)))
        db.classDao().insertAll(
            listOf(
                ClassEntity(1, 1, 1, 1_900_000_000_000, 90, priceMinor = 5000, status = "COMPLETED", chargeMinor = 5000, recurringGroupId = 42, mode = "REMOTE", calendarEventId = "evt-1"),
                ClassEntity(2, 2, 1, 1_900_100_000_000, 60, status = "SCHEDULED"),
            ),
        )
        db.paymentDao().insertAll(listOf(PaymentEntity(1, 1, 1_899_000_000_000, 3000)))

        val original = manager.export()
        val jsonText = manager.exportJson()

        // Import the JSON into a completely fresh, empty database.
        val emptyDb = newDb()
        val reproduced = try {
            val emptyManager = managerFor(emptyDb)
            emptyManager.importJson(jsonText)
            emptyManager.export()
        } finally {
            emptyDb.close()
        }

        assertEquals(original, reproduced)
    }
}
