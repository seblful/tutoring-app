package com.seblful.klas.data.backup

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.HomeworkEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupLegacyHomeworkTest {

    private fun lesson(id: Long, start: Long, homework: String, groupId: Long? = null, status: LessonStatus = LessonStatus.COMPLETED) =
        BackupLesson(id, subjectId = 1, startMillis = start, durationMinutes = 60, status = status, groupId = groupId, homework = homework)

    private fun member(id: Long, lessonId: Long, studentId: Long, verdict: String? = null) =
        BackupAttendance(id, lessonId, studentId, state = Attended.PRESENT, homeworkResult = verdict)

    @Test
    fun `an individual lesson's homework becomes its student's, given at the lesson's start`() {
        val rows =
            legacyHomework(listOf(lesson(4, start = 1_000, homework = "§12")), listOf(member(9, 4, studentId = 3)))

        assertEquals(listOf(HomeworkEntity(studentId = 3, text = "§12", givenAtMillis = 1_000)), rows)
    }

    /** Any verdict meant "checked" — «частично» and «не сделано» too — and checked is what done is now. */
    @Test
    fun `any verdict makes it done`() {
        val rows = legacyHomework(
            listOf(lesson(4, 1_000, "§12"), lesson(5, 2_000, "§13"), lesson(6, 3_000, "§14")),
            listOf(member(9, 4, 3, "DONE"), member(10, 5, 3, "NOT_DONE"), member(11, 6, 3)),
        )

        assertEquals(listOf(1_000L, 2_000L, null), rows.map { it.doneAtMillis })
    }

    @Test
    fun `a group lesson's homework is the group's, done once every member has a verdict`() {
        val lessons = listOf(lesson(4, 1_000, "Задачи", groupId = 7), lesson(5, 2_000, "Ещё", groupId = 7))
        val attendance = listOf(
            member(9, 4, 1, "DONE"), member(10, 4, 2, "PARTIAL"),
            member(11, 5, 1, "DONE"), member(12, 5, 2),
        )

        val rows = legacyHomework(lessons, attendance)

        assertEquals(
            listOf(
                HomeworkEntity(groupId = 7, text = "Задачи", givenAtMillis = 1_000, doneAtMillis = 1_000),
                HomeworkEntity(groupId = 7, text = "Ещё", givenAtMillis = 2_000),
            ),
            rows,
        )
    }

    @Test
    fun `a lesson that set nothing, or was called off, gives no homework`() {
        val rows = legacyHomework(
            listOf(lesson(4, 1_000, ""), lesson(5, 2_000, "§12", status = LessonStatus.CANCELLED)),
            listOf(member(9, 4, 3, "DONE"), member(10, 5, 3)),
        )

        assertTrue(rows.isEmpty())
    }

    /** Restored whole: the homework in its own rows, and the lesson and attendance as the tables hold them. */
    @Test
    fun `a version 8 file restores its homework as rows, and its lessons without it`() {
        val v8 = """
            {
              "version": 8,
              "subjects": [{"id": 1, "name": "Математика"}],
              "students": [{"id": 1, "name": "Иван"}],
              "lessons": [{"id": 4, "subjectId": 1, "startMillis": 1000, "durationMinutes": 60,
                           "status": "COMPLETED", "notes": "Не готовился", "homework": "§12, упр. 4–7"}],
              "attendance": [{"id": 9, "lessonId": 4, "studentId": 1, "state": "PRESENT",
                              "homeworkResult": "PARTIAL"}]
            }
        """.trimIndent()

        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString(BackupData.serializer(), v8).toSnapshot()

        assertEquals(
            listOf(HomeworkEntity(studentId = 1, text = "§12, упр. 4–7", givenAtMillis = 1000, doneAtMillis = 1000)),
            snapshot.homework,
        )
        assertEquals(
            LessonEntity(4, subjectId = 1, startMillis = 1000, durationMinutes = 60, status = LessonStatus.COMPLETED),
            snapshot.lessons.single(),
        )
        assertEquals(AttendanceEntity(9, lessonId = 4, studentId = 1, state = Attended.PRESENT), snapshot.attendance.single())
    }

    /** A v9 file keeps homework in its own list, and its lessons' are blank — nothing is added twice. */
    @Test
    fun `a version 9 file's homework is carried as it is`() {
        val own = HomeworkEntity(5, studentId = 1, text = "§12", givenAtMillis = 1000)
        val data = BackupData(lessons = listOf(lesson(4, 1_000, homework = "")), homework = listOf(own))

        assertEquals(listOf(own), data.toSnapshot().homework)
    }
}
