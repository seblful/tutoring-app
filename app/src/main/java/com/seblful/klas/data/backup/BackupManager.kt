package com.seblful.klas.data.backup

import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.PaymentDao
import com.seblful.klas.data.local.PriceDao
import com.seblful.klas.data.local.RateHistoryDao
import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.SubjectDao
import com.seblful.klas.data.local.Transactor
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
    private val transactor: Transactor,
    private val subjects: SubjectDao,
    private val students: StudentDao,
    private val groups: GroupDao,
    private val prices: PriceDao,
    private val rateHistory: RateHistoryDao,
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
        groups = groups.getAll(),
        groupMembers = groups.getAllMembers(),
        prices = prices.getAll(),
        rateHistory = rateHistory.getAll(),
        classes = classes.getAll(),
        payments = payments.getAll(),
    )

    suspend fun exportJson(): String = json.encodeToString(export())

    fun parse(text: String): BackupData = json.decodeFromString(text)

    suspend fun import(data: BackupData) {
        if (!BackupData.isSupported(data.version)) {
            throw UnsupportedBackupVersionException(
                found = data.version,
                supported = BackupData.CURRENT_VERSION,
            )
        }
        // Both refusals happen before anything is deleted. The version says whether this build
        // understands the *format*; the integrity check says whether it can read the *rows* — which
        // matters because the rows go in verbatim and are only interpreted later, on the read path.
        // See [BackupIntegrity].
        BackupIntegrity.check(data)
        transactor.transaction {
            // Children first, then parents, to respect foreign keys.
            payments.deleteAll()
            classes.deleteAll()
            rateHistory.deleteAll()
            prices.deleteAll()
            groups.deleteAllMembers()
            groups.deleteAll()
            students.deleteAllLinks()
            students.deleteAll()
            subjects.deleteAll()

            // Parents first on the way back in.
            subjects.insertAll(data.subjects)
            students.insertAll(data.students)
            students.insertLinks(data.studentSubjects)
            groups.insertAll(data.groups)
            groups.insertMembers(data.groupMembers)
            prices.insertAll(data.prices)
            rateHistory.insertAll(data.rateHistory)
            classes.insertAll(data.classes)
            payments.insertAll(data.payments)
        }
    }

    suspend fun importJson(text: String) = import(parse(text))
}
