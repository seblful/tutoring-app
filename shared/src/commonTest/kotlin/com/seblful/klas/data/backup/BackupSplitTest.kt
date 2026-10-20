package com.seblful.klas.data.backup

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.TestEnv
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupSplitTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()

    private val t0 = 1_760_000_000_000L

    private fun legacy(
        id: Long,
        studentId: Long,
        status: LessonStatus,
        groupId: Long? = null,
        startMillis: Long = t0,
        durationMinutes: Int = 60,
        priceMinor: Long = 3_000_00,
        chargeMinor: Long = 0,
    ) = LegacyClass(
        id = id,
        studentId = studentId,
        subjectId = 1,
        startMillis = startMillis,
        durationMinutes = durationMinutes,
        priceMinor = priceMinor,
        status = status,
        chargeMinor = chargeMinor,
        groupId = groupId,
    )

    @Test
    fun `the lesson-status precedence is scheduled - then completed - then cancelled`() {
        assertEquals(
            listOf(LessonStatus.SCHEDULED, LessonStatus.COMPLETED, LessonStatus.CANCELLED),
            LessonStatus.PRECEDENCE,
        )
    }

    @Test
    fun `an individual class becomes its own lesson - keeping every id`() {
        val (lessons, attendance) = listOf(
            legacy(id = 5, studentId = 2, status = LessonStatus.COMPLETED, chargeMinor = 3_000_00),
        ).splitIntoLessons()

        assertEquals(1, lessons.size)
        assertEquals(5L, lessons.single().id)
        assertEquals(LessonStatus.COMPLETED, lessons.single().status)
        assertEquals(t0, lessons.single().startMillis)
        assertEquals(5L, attendance.single().id)
        assertEquals(5L, attendance.single().lessonId)
        assertEquals(Attended.PRESENT, attendance.single().state)
        assertEquals(3_000_00L, attendance.single().chargeMinor)
    }

    @Test
    fun `a group's members at one time become one lesson taking the lowest id`() {
        val (lessons, attendance) = listOf(
            legacy(id = 8, studentId = 1, status = LessonStatus.SCHEDULED, groupId = 7),
            legacy(id = 3, studentId = 2, status = LessonStatus.SCHEDULED, groupId = 7),
            legacy(id = 9, studentId = 3, status = LessonStatus.SCHEDULED, groupId = 7),
        ).splitIntoLessons()

        assertEquals(1, lessons.size)
        assertEquals(3L, lessons.single().id, "the lesson takes the lowest of its classes' ids")
        assertEquals(7L, lessons.single().groupId)
        assertEquals(listOf(3L, 8L, 9L), attendance.map { it.id })
        assertTrue(attendance.all { it.lessonId == 3L }, "every member points at the one lesson")
    }

    /**
     * The case the precedence exists for, and the one that decides money: one member scheduled, one
     * completed. The hour has **not** happened, so the lesson is scheduled — and the member whose class
     * said completed is not thereby marked present at a lesson still to come.
     */
    @Test
    fun `a group lesson with one member still scheduled is a scheduled lesson`() {
        val (lessons, attendance) = listOf(
            legacy(id = 1, studentId = 1, status = LessonStatus.COMPLETED, groupId = 7, chargeMinor = 3_000_00),
            legacy(id = 2, studentId = 2, status = LessonStatus.SCHEDULED, groupId = 7),
        ).splitIntoLessons()

        assertEquals(LessonStatus.SCHEDULED, lessons.single().status)
        assertEquals(
            mapOf(1L to Attended.PRESENT, 2L to Attended.EXPECTED),
            attendance.associate { it.id to it.state },
        )
    }

    @Test
    fun `a taught lesson whose absentee was cancelled keeps the absence`() {
        // The correction the old model could only express by cancelling one member's class — which also
        // stopped charging them. It comes back as a taught lesson with an absentee.
        val (lessons, attendance) = listOf(
            legacy(id = 1, studentId = 1, status = LessonStatus.COMPLETED, groupId = 7, chargeMinor = 3_000_00),
            legacy(id = 2, studentId = 2, status = LessonStatus.CANCELLED, groupId = 7),
        ).splitIntoLessons()

        assertEquals(LessonStatus.COMPLETED, lessons.single().status)
        assertEquals(
            mapOf(1L to Attended.PRESENT, 2L to Attended.ABSENT),
            attendance.associate { it.id to it.state },
        )
    }

    @Test
    fun `a lesson called off for everyone leaves nobody absent from it`() {
        // Nobody missed an hour that did not happen, so every member is merely expected — which is what
        // makes "called off" and "nobody came" different rows at last.
        val (lessons, attendance) = listOf(
            legacy(id = 1, studentId = 1, status = LessonStatus.CANCELLED, groupId = 7),
            legacy(id = 2, studentId = 2, status = LessonStatus.CANCELLED, groupId = 7),
        ).splitIntoLessons()

        assertEquals(LessonStatus.CANCELLED, lessons.single().status)
        assertTrue(attendance.all { it.state == Attended.EXPECTED })
    }

    @Test
    fun `the same group at two times is two lessons`() {
        val later = t0 + 7 * 24 * 60 * 60 * 1000L
        val (lessons, attendance) = listOf(
            legacy(id = 1, studentId = 1, status = LessonStatus.SCHEDULED, groupId = 7),
            legacy(id = 2, studentId = 2, status = LessonStatus.SCHEDULED, groupId = 7),
            legacy(id = 3, studentId = 1, status = LessonStatus.SCHEDULED, groupId = 7, startMillis = later),
            legacy(id = 4, studentId = 2, status = LessonStatus.SCHEDULED, groupId = 7, startMillis = later),
        ).splitIntoLessons()

        assertEquals(listOf(1L, 3L), lessons.map { it.id })
        assertEquals(
            mapOf(1L to 1L, 2L to 1L, 3L to 3L, 4L to 3L),
            attendance.associate { it.id to it.lessonId },
        )
    }

    /**
     * The split used to have to survive a status it could not read — it runs inside `BackupIntegrity.check`,
     * which then refused the file. A legacy class's status is typed now, so such a row never reaches it: the
     * file is refused as it is parsed, and as the same failure the tutor was shown before.
     */
    @Test
    fun `an unreadable status never reaches the split - the file is refused as it is parsed`() {
        val file = """
            {"version": 5, "classes": [{"id": 1, "studentId": 1, "subjectId": 1, "startMillis": $t0,
              "durationMinutes": 60, "status": "MISSED"}]}
        """.trimIndent()

        val error = runCatching { env.backupManager.parse(file) }.exceptionOrNull()

        assertEquals(BackupFailure.Malformed, error?.let(BackupFailure::of))
    }

    @Test
    fun `nothing in becomes nothing out`() {
        val (lessons, attendance) = emptyList<LegacyClass>().splitIntoLessons()

        assertTrue(lessons.isEmpty())
        assertTrue(attendance.isEmpty())
    }
}
