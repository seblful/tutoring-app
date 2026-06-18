package com.seblful.tutoringapp.data.backup

import androidx.room.withTransaction
import com.seblful.tutoringapp.data.local.AppDatabase
import com.seblful.tutoringapp.data.local.ClassDao
import com.seblful.tutoringapp.data.local.PaymentDao
import com.seblful.tutoringapp.data.local.PriceDao
import com.seblful.tutoringapp.data.local.StudentDao
import com.seblful.tutoringapp.data.local.SubjectDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports the whole database to a JSON string and restores it. Import is a full replace inside
 * a single transaction, so a failure leaves the existing data untouched (no partial restore).
 */
@Singleton
class BackupManager @Inject constructor(
    private val db: AppDatabase,
    private val subjects: SubjectDao,
    private val students: StudentDao,
    private val prices: PriceDao,
    private val classes: ClassDao,
    private val payments: PaymentDao,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(): BackupData = BackupData(
        subjects = subjects.getAll(),
        students = students.getAll(),
        studentSubjects = students.getAllLinks(),
        prices = prices.getAll(),
        classes = classes.getAll(),
        payments = payments.getAll(),
    )

    suspend fun exportJson(): String = json.encodeToString(export())

    fun parse(text: String): BackupData = json.decodeFromString(text)

    suspend fun import(data: BackupData) {
        db.withTransaction {
            // Children first, then parents, to respect foreign keys.
            payments.deleteAll()
            classes.deleteAll()
            prices.deleteAll()
            students.deleteAllLinks()
            students.deleteAll()
            subjects.deleteAll()

            // Parents first on the way back in.
            subjects.insertAll(data.subjects)
            students.insertAll(data.students)
            students.insertLinks(data.studentSubjects)
            prices.insertAll(data.prices)
            classes.insertAll(data.classes)
            payments.insertAll(data.payments)
        }
    }

    suspend fun importJson(text: String) = import(parse(text))
}
