package com.seblful.tutoringapp.data.backup

import com.seblful.tutoringapp.data.local.ClassEntity
import com.seblful.tutoringapp.data.local.PaymentEntity
import com.seblful.tutoringapp.data.local.PriceEntity
import com.seblful.tutoringapp.data.local.StudentEntity
import com.seblful.tutoringapp.data.local.StudentSubjectCrossRef
import com.seblful.tutoringapp.data.local.SubjectEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the JSON backup format reproduces every field of every row exactly (criterion 6). */
class BackupSerializationTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val sample = BackupData(
        subjects = listOf(SubjectEntity(1, "Математика"), SubjectEntity(2, "Физика")),
        students = listOf(
            StudentEntity(1, "Иван", phone = "+375291112233", note = "ОГЭ", defaultMode = "REMOTE", archived = false),
            StudentEntity(2, "Мария", note = "", archived = true),
        ),
        studentSubjects = listOf(
            StudentSubjectCrossRef(1, 1),
            StudentSubjectCrossRef(1, 2),
            StudentSubjectCrossRef(2, 1),
        ),
        prices = listOf(
            PriceEntity(1, 1, amountMinor = 5000),
            PriceEntity(2, 1, amountMinor = 4550), // 45,50 BYN — kopecks survive
        ),
        classes = listOf(
            ClassEntity(
                id = 1, studentId = 1, subjectId = 1, startMillis = 1_900_000_000_000,
                durationMinutes = 90, status = "COMPLETED", chargeMinor = 5000,
                recurringGroupId = 42, mode = "REMOTE", calendarEventId = "evt-123",
            ),
            ClassEntity(
                id = 2, studentId = 2, subjectId = 1, startMillis = 1_900_100_000_000,
                durationMinutes = 60, status = "SCHEDULED",
            ),
        ),
        payments = listOf(
            PaymentEntity(1, studentId = 1, dateMillis = 1_899_000_000_000, amountMinor = 3000),
        ),
    )

    @Test
    fun `round-trips every table through JSON unchanged`() {
        val restored = json.decodeFromString<BackupData>(json.encodeToString(sample))
        assertEquals(sample, restored)
    }

    @Test
    fun `default version is stamped`() {
        val restored = json.decodeFromString<BackupData>(json.encodeToString(sample))
        assertEquals(BackupData.CURRENT_VERSION, restored.version)
    }
}
