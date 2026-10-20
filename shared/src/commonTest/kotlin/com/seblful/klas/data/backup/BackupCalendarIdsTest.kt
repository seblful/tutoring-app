package com.seblful.klas.data.backup

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupCalendarIdsTest {
    @Test
    fun `restoring current files discards event ids in every lesson status`() {
        LessonStatus.entries.forEach { status ->
            val row = BackupAttendance(
                id = 9, lessonId = 4, studentId = 1, state = Attended.PRESENT,
                priceMinor = 3000, chargeMinor = 3000, calendarEventId = "42",
            )
            val file = BackupData(
                lessons = listOf(BackupLesson(4, 1, 1, 60, status)),
                attendance = listOf(row),
            )

            assertEquals(row.toEntity().copy(calendarEventId = null), file.toSnapshot().attendance.single())
        }
    }

    @Test
    fun `restoring legacy files also discards foreign device event ids`() {
        val row = LegacyClass(
            id = 9, studentId = 1, subjectId = 1, startMillis = 1, durationMinutes = 60,
            status = LessonStatus.CANCELLED, calendarEventId = "42",
        )
        val expected = listOf(row).splitIntoLessons().second.single().copy(calendarEventId = null)

        assertEquals(expected, BackupData(version = 5, classes = listOf(row)).toSnapshot().attendance.single())
    }
}
