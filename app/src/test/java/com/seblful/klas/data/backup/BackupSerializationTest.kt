package com.seblful.klas.data.backup

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
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
        rateHistory = listOf(
            // A student's price: where it started (no previous amount), then a rise.
            RateChangeEntity(1, studentId = 1, subjectId = 1, toMinor = 4000, atMillis = 1_890_000_000_000),
            RateChangeEntity(2, studentId = 1, subjectId = 1, fromMinor = 4000, toMinor = 5000, atMillis = 1_895_000_000_000),
            // A group's rate, and a row that predates the log at all — both nulls must survive.
            RateChangeEntity(3, groupId = 7, fromMinor = 1800, toMinor = 2050, atMillis = 1_896_000_000_000),
            RateChangeEntity(4, groupId = 8, toMinor = 0, atMillis = null),
        ),
        lessons = listOf(
            LessonEntity(
                id = 1, subjectId = 1, startMillis = 1_900_000_000_000, durationMinutes = 90,
                status = "COMPLETED", groupId = 7, mode = "REMOTE",
            ),
            LessonEntity(
                id = 2, subjectId = 1, startMillis = 1_900_100_000_000, durationMinutes = 60,
                status = "SCHEDULED",
            ),
        ),
        attendance = listOf(
            // The taught lesson, with one of its two members absent — the pair of facts a v5 file could
            // not carry, and so the pair a round trip most needs to prove survives.
            AttendanceEntity(
                id = 1, lessonId = 1, studentId = 1, state = "PRESENT",
                priceMinor = 2000, chargeMinor = 5000, calendarEventId = "evt-123",
            ),
            AttendanceEntity(id = 2, lessonId = 1, studentId = 2, state = "ABSENT", priceMinor = 2000),
            AttendanceEntity(id = 3, lessonId = 2, studentId = 2, state = "EXPECTED"),
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
     * its groups coming back at 0 for the tutor to re-price, rather than inventing an amount for a
     * group whose members were billed individually.
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

    /**
     * A v4 backup predates the rate log, so it arrives with an empty one. Seeding it from the prices
     * in the file is deliberately *not* done: a restore reproduces the file it was given, and
     * inventing "начальная цена" rows for a file that never recorded them would put dates and
     * amounts in a history that nothing attests to.
     */
    @Test
    fun `a version 4 backup still parses, with no rate history`() {
        val v4 = """
            {
              "version": 4,
              "subjects": [{"id": 1, "name": "Математика"}],
              "students": [{"id": 1, "name": "Иван"}],
              "prices": [{"studentId": 1, "subjectId": 1, "amountMinor": 5000}],
              "classes": [],
              "payments": []
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BackupData>(v4)

        assertTrue(BackupData.isSupported(parsed.version))
        assertTrue("no log is not a broken log", parsed.rateHistory.isEmpty())
        assertEquals("the price itself is intact", 5000L, parsed.prices.single().amountMinor)
    }

    /**
     * A v7 backup predates homework: its lessons come back with none set and its students with no verdict,
     * and the note — which then held homework too — comes back exactly as written, not split by guesswork.
     */
    @Test
    fun `a version 7 backup still parses, with no homework`() {
        val v7 = """
            {
              "version": 7,
              "subjects": [{"id": 1, "name": "Математика"}],
              "students": [{"id": 1, "name": "Иван"}],
              "lessons": [{"id": 4, "subjectId": 1, "startMillis": 1, "durationMinutes": 60,
                           "status": "COMPLETED", "notes": "§12, упр. 4–7"}],
              "attendance": [{"id": 9, "lessonId": 4, "studentId": 1, "state": "PRESENT"}]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BackupData>(v7)

        assertTrue(BackupData.isSupported(parsed.version))
        assertEquals("§12, упр. 4–7", parsed.lessons.single().notes)
        assertEquals("", parsed.lessons.single().homework)
        assertEquals(null, parsed.attendance.single().homeworkResult)
    }

    @Test
    fun `only current-or-older backup versions are importable`() {
        assertTrue(BackupData.isSupported(BackupData.CURRENT_VERSION))
        assertFalse(BackupData.isSupported(BackupData.CURRENT_VERSION + 1)) // newer = reject, don't drop data
        assertFalse(BackupData.isSupported(0)) // malformed
    }
}
