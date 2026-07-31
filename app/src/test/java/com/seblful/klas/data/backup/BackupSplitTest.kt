package com.seblful.klas.data.backup

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Splitting a v1–v5 file's flat `classes` list into `lessons` + `attendance` — the test `splitIntoLessons`
 * has always cited and never had.
 *
 * **Why this matters more than its size suggests.** The same split exists twice: in SQL as
 * `MIGRATION_1_2`, against a live database, and in Kotlin here, over parsed JSON. They cannot share an
 * implementation, so what they share has to be pinned instead. Until this file existed the Kotlin half's
 * *output* was asserted nowhere at all — the three legacy-version cases in `BackupSerializationTest`
 * check only that such a file parses, and never call `toSnapshot()`; the function otherwise ran only
 * inside `BackupIntegrity.check`, whose whole purpose is to decide whether to **refuse** the rows.
 *
 * And the rule it applies decides money. A group lesson with one scheduled member and one completed one
 * is *scheduled* — the hour has not happened — and reading it the other way round recognises income the
 * app never earned, silently, on the one path a tutor reaches for after losing a phone.
 */
class BackupSplitTest {

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
        status = status.name,
        chargeMinor = chargeMinor,
        groupId = groupId,
    )

    // --- the precedence, and the two places it is stated ---

    /**
     * The precedence itself, as a literal. Deliberately not derived from `LessonStatus.entries`: the
     * whole point of [LessonStatus.PRECEDENCE] is that it stopped *being* the declaration order, and a
     * test that read the enum back would pin nothing.
     */
    @Test
    fun `the lesson-status precedence is scheduled, then completed, then cancelled`() {
        assertEquals(
            listOf(LessonStatus.SCHEDULED, LessonStatus.COMPLETED, LessonStatus.CANCELLED),
            LessonStatus.PRECEDENCE,
        )
    }

    /**
     * The SQL half says the same thing, in the same order.
     *
     * Read out of the migration's source because a `CASE` expression inside an `execSQL` string is
     * reachable no other way, and because *this* is the pairing that can drift: the two halves are
     * separate implementations of one rule, and only one of them has a Kotlin compiler checking it.
     */
    @Test
    fun `the migration's SQL applies the same precedence in the same order`() {
        val sql = ProjectFiles.sourceFile("data/local/Migrations.kt").readText()
        val order = Regex("""WHEN SUM\(c\.status = '(\w+)'\) > 0 THEN '(\w+)'""")
            .findAll(sql)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

        assertTrue("MIGRATION_1_2's status CASE was not found — has it moved?", order.isNotEmpty())
        // Each arm tests for a status and yields the same one, in precedence order...
        assertEquals(LessonStatus.PRECEDENCE.dropLast(1).map { it.name to it.name }, order)
        // ...and the last one is the ELSE, which is why it is not in the list above.
        assertTrue(
            "the fallback arm must be the last of the precedence",
            sql.contains("ELSE '${LessonStatus.PRECEDENCE.last().name}'"),
        )
    }

    // --- what the split produces ---

    @Test
    fun `an individual class becomes its own lesson, keeping every id`() {
        val (lessons, attendance) = listOf(
            legacy(id = 5, studentId = 2, status = LessonStatus.COMPLETED, chargeMinor = 3_000_00),
        ).splitIntoLessons()

        assertEquals(1, lessons.size)
        assertEquals(5L, lessons.single().id)
        assertEquals(LessonStatus.COMPLETED.name, lessons.single().status)
        assertEquals(t0, lessons.single().startMillis)
        // The class's own id survives as its attendance id, so a calendar event or a charge still points
        // where it did; the row is tied to the lesson that its class became.
        assertEquals(5L, attendance.single().id)
        assertEquals(5L, attendance.single().lessonId)
        assertEquals(Attended.PRESENT.name, attendance.single().state)
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
        assertEquals("the lesson takes the lowest of its classes' ids", 3L, lessons.single().id)
        assertEquals(7L, lessons.single().groupId)
        assertEquals(listOf(3L, 8L, 9L), attendance.map { it.id })
        assertTrue("every member points at the one lesson", attendance.all { it.lessonId == 3L })
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

        assertEquals(LessonStatus.SCHEDULED.name, lessons.single().status)
        assertEquals(
            mapOf(1L to Attended.PRESENT.name, 2L to Attended.EXPECTED.name),
            attendance.associate { it.id to it.state },
        )
    }

    @Test
    fun `a taught lesson whose absentee was cancelled keeps the absence`() {
        // The correction the old model could only express by cancelling one member's class — which also
        // stopped charging them. It comes back as a taught lesson with an absentee, per `MIGRATION_1_2`.
        val (lessons, attendance) = listOf(
            legacy(id = 1, studentId = 1, status = LessonStatus.COMPLETED, groupId = 7, chargeMinor = 3_000_00),
            legacy(id = 2, studentId = 2, status = LessonStatus.CANCELLED, groupId = 7),
        ).splitIntoLessons()

        assertEquals(LessonStatus.COMPLETED.name, lessons.single().status)
        assertEquals(
            mapOf(1L to Attended.PRESENT.name, 2L to Attended.ABSENT.name),
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

        assertEquals(LessonStatus.CANCELLED.name, lessons.single().status)
        assertTrue(attendance.all { it.state == Attended.EXPECTED.name })
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

    @Test
    fun `an unreadable status is left for the integrity check to refuse, not crashed on`() {
        // `splitIntoLessons` runs *inside* `BackupIntegrity.check`, so it has to survive the very row that
        // check exists to reject: throwing here would report "wouldn't open" instead of naming the file.
        val rows = listOf(
            LegacyClass(
                id = 1,
                studentId = 1,
                subjectId = 1,
                startMillis = t0,
                durationMinutes = 60,
                status = "MISSED",
            ),
        )

        val (lessons, attendance) = rows.splitIntoLessons()

        // Which of the three it lands on is not a rule anybody relies on — the file is refused before a
        // row of it is written. What is pinned here is that it lands somewhere readable rather than
        // throwing, and that "unreadable" is not quietly read as one of the real statuses.
        assertEquals(LessonStatus.SCHEDULED.name, lessons.single().status)
        assertEquals(Attended.ABSENT.name, attendance.single().state)
        // And the file is still refused, by the check that runs this very split on its way to deciding so.
        assertThrows(MalformedBackupException::class.java) {
            BackupIntegrity.check(BackupData(classes = rows))
        }
    }

    @Test
    fun `nothing in becomes nothing out`() {
        val (lessons, attendance) = emptyList<LegacyClass>().splitIntoLessons()

        assertTrue(lessons.isEmpty())
        assertTrue(attendance.isEmpty())
    }
}
