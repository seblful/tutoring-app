package com.seblful.repetit.data.backup

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.GroupEntity
import com.seblful.repetit.data.local.GroupMemberCrossRef
import com.seblful.repetit.data.local.PaymentEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.StudentSubjectCrossRef
import com.seblful.repetit.data.local.SubjectEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        groups = listOf(
            GroupEntity(7, "11 «А»", note = "вечер", priceMinor = 2050, defaultMode = "REMOTE", archived = false),
            GroupEntity(8, "Выпускники", archived = true),
        ),
        groupMembers = listOf(
            GroupMemberCrossRef(7, 1),
            GroupMemberCrossRef(7, 2),
            GroupMemberCrossRef(8, 2),
        ),
        prices = listOf(
            PriceEntity(1, 1, amountMinor = 5000),
            PriceEntity(2, 1, amountMinor = 4550), // 45,50 BYN — kopecks survive
        ),
        classes = listOf(
            ClassEntity(
                id = 1, studentId = 1, subjectId = 1, startMillis = 1_900_000_000_000,
                durationMinutes = 90, status = "COMPLETED", chargeMinor = 5000,
                groupId = 7, mode = "REMOTE", calendarEventId = "evt-123",
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

    /**
     * A backup written before groups existed must still import: no groups, no group id on its
     * classes, and the retired `recurringGroupId` key simply ignored.
     */
    @Test
    fun `a version 1 backup still parses, without groups`() {
        val v1 = """
            {
              "version": 1,
              "subjects": [{"id": 1, "name": "Математика"}],
              "students": [{"id": 1, "name": "Иван"}],
              "studentSubjects": [{"studentId": 1, "subjectId": 1}],
              "prices": [{"studentId": 1, "subjectId": 1, "amountMinor": 5000}],
              "classes": [{
                "id": 1, "studentId": 1, "subjectId": 1, "startMillis": 1900000000000,
                "durationMinutes": 60, "status": "SCHEDULED", "recurringGroupId": null
              }],
              "payments": []
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BackupData>(v1)

        assertTrue(BackupData.isSupported(parsed.version))
        assertTrue(parsed.groups.isEmpty())
        assertTrue(parsed.groupMembers.isEmpty())
        assertEquals(null, parsed.classes.single().groupId)
    }

    /**
     * A v2 backup — groups, but from before a group had a rate of its own — must still import, with
     * its groups coming back at 0 for the tutor to re-price. That mirrors what the v4 → v5 database
     * migration does, rather than inventing an amount for a group whose members were billed
     * individually.
     */
    @Test
    fun `a version 2 backup still parses, its groups left unpriced`() {
        val v2 = """
            {
              "version": 2,
              "subjects": [{"id": 1, "name": "Математика"}],
              "students": [{"id": 1, "name": "Иван"}],
              "groups": [{"id": 7, "name": "11 «А»", "note": "", "defaultMode": "IN_PERSON", "archived": false}],
              "groupMembers": [{"groupId": 7, "studentId": 1}],
              "classes": [{
                "id": 1, "studentId": 1, "subjectId": 1, "startMillis": 1900000000000,
                "durationMinutes": 60, "priceMinor": 3000, "status": "COMPLETED", "groupId": 7
              }],
              "payments": []
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BackupData>(v2)

        assertTrue(BackupData.isSupported(parsed.version))
        assertEquals(0L, parsed.groups.single().priceMinor)
        // The lesson keeps the rate it was actually billed at — history is never rewritten.
        assertEquals(3000L, parsed.classes.single().priceMinor)
    }

    @Test
    fun `only current-or-older backup versions are importable`() {
        assertTrue(BackupData.isSupported(BackupData.CURRENT_VERSION))
        assertFalse(BackupData.isSupported(BackupData.CURRENT_VERSION + 1)) // newer = reject, don't drop data
        assertFalse(BackupData.isSupported(0)) // malformed
    }
}
