package com.seblful.klas.data.backup

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.HomeworkEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            StudentEntity(1, "Иван", phone = "+375291112233", note = "ОГЭ", defaultMode = ClassMode.REMOTE, archived = false),
            StudentEntity(2, "Мария", note = "", archived = true),
        ),
        studentSubjects = listOf(
            StudentSubjectCrossRef(1, 1),
            StudentSubjectCrossRef(1, 2),
            StudentSubjectCrossRef(2, 1),
        ),
        groups = listOf(
            GroupEntity(7, "11 «А»", note = "вечер", priceMinor = 2050, defaultMode = ClassMode.REMOTE, archived = false),
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
            BackupLesson(
                id = 1, subjectId = 1, startMillis = 1_900_000_000_000, durationMinutes = 90,
                status = LessonStatus.COMPLETED, groupId = 7, mode = ClassMode.REMOTE,
            ),
            BackupLesson(
                id = 2, subjectId = 1, startMillis = 1_900_100_000_000, durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            ),
        ),
        attendance = listOf(
            // The taught lesson, with one of its two members absent — the pair of facts a v5 file could
            // not carry, and so the pair a round trip most needs to prove survives.
            BackupAttendance(
                id = 1, lessonId = 1, studentId = 1, state = Attended.PRESENT,
                priceMinor = 2000, chargeMinor = 5000, calendarEventId = "evt-123",
            ),
            BackupAttendance(id = 2, lessonId = 1, studentId = 2, state = Attended.ABSENT, priceMinor = 2000),
            BackupAttendance(id = 3, lessonId = 2, studentId = 2, state = Attended.EXPECTED),
        ),
        payments = listOf(
            PaymentEntity(1, studentId = 1, dateMillis = 1_899_000_000_000, amountMinor = 3000),
        ),
        homework = listOf(
            // One student's, done — and a group's, current: both owners, and the null that means current.
            HomeworkEntity(
                1, studentId = 1, text = "§12, упр. 4–7",
                givenAtMillis = 1_900_000_000_000, doneAtMillis = 1_900_100_000_000,
            ),
            HomeworkEntity(2, groupId = 7, text = "Задачи 31–35", givenAtMillis = 1_900_050_000_000),
        ),
    )

    @Test
    fun `round-trips every table through JSON unchanged`() {
        val restored = json.decodeFromString<BackupData>(json.encodeToString(sample))
        assertEquals(sample, restored)
    }

    /**
     * The enum columns are typed, and the file still carries each as the bare constant name — the JSON
     * written before they were typed, and the JSON every file already on somebody's disk holds. A round trip
     * alone could not show it: a serializer that wrote something else would read its own output back fine.
     */
    @Test
    fun `enum columns are written as their constant names, as they always were`() {
        val tree = json.parseToJsonElement(json.encodeToString(sample)).jsonObject
        fun row(table: String, index: Int = 0) = tree.getValue(table).jsonArray[index].jsonObject
        fun text(table: String, key: String, index: Int = 0) = row(table, index).getValue(key).jsonPrimitive
            .also { assertTrue("$table.$key is a JSON string", it.isString) }
            .content

        assertEquals("REMOTE", text("students", "defaultMode"))
        assertEquals("IN_PERSON", text("students", "defaultMode", index = 1))
        assertEquals("REMOTE", text("groups", "defaultMode"))
        assertEquals("COMPLETED", text("lessons", "status"))
        assertEquals("REMOTE", text("lessons", "mode"))
        assertEquals("SCHEDULED", text("lessons", "status", index = 1))
        assertEquals("IN_PERSON", text("lessons", "mode", index = 1))
        assertEquals("PRESENT", text("attendance", "state"))
        assertEquals("ABSENT", text("attendance", "state", index = 1))
        assertEquals("EXPECTED", text("attendance", "state", index = 2))
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
     * A v7 backup predates homework: it restores with none, and without its notes — the one thing an older
     * file carries that this build no longer has anywhere to put.
     */
    @Test
    fun `a version 7 backup still restores, with no homework and no notes`() {
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
        val snapshot = parsed.toSnapshot()

        assertTrue(BackupData.isSupported(parsed.version))
        assertEquals(
            LessonEntity(4, subjectId = 1, startMillis = 1, durationMinutes = 60, status = LessonStatus.COMPLETED),
            snapshot.lessons.single(),
        )
        assertTrue(snapshot.homework.isEmpty())
    }

    /**
     * A v9 file as a v9 build wrote it — the three dead columns present and blank, since that build still
     * had them — restores exactly as one written now, which leaves them out (`BackupSchemaCoverageTest`).
     */
    @Test
    fun `a version 9 backup carrying the dropped columns blank restores the same rows`() {
        val v9 = """
            {
              "version": 9,
              "subjects": [{"id": 1, "name": "Математика"}],
              "students": [{"id": 1, "name": "Иван"}],
              "lessons": [{"id": 4, "subjectId": 1, "startMillis": 1, "durationMinutes": 60,
                           "status": "COMPLETED", "groupId": null, "mode": "REMOTE", "notes": "", "homework": ""}],
              "attendance": [{"id": 9, "lessonId": 4, "studentId": 1, "state": "PRESENT", "priceMinor": 3000,
                              "chargeMinor": 3000, "calendarEventId": "evt-1", "homeworkResult": null}],
              "homework": []
            }
        """.trimIndent()

        val snapshot = json.decodeFromString<BackupData>(v9).toSnapshot()

        assertEquals(
            LessonEntity(4, 1, startMillis = 1, durationMinutes = 60, status = LessonStatus.COMPLETED, mode = ClassMode.REMOTE),
            snapshot.lessons.single(),
        )
        assertEquals(
            AttendanceEntity(9, 4, 1, Attended.PRESENT, priceMinor = 3000, chargeMinor = 3000),
            snapshot.attendance.single(),
        )
        assertTrue(snapshot.homework.isEmpty())
    }

    @Test
    fun `only current-or-older backup versions are importable`() {
        assertTrue(BackupData.isSupported(BackupData.CURRENT_VERSION))
        assertFalse(BackupData.isSupported(BackupData.CURRENT_VERSION + 1)) // newer = reject, don't drop data
        assertFalse(BackupData.isSupported(0)) // malformed
    }
}
