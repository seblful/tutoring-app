package com.seblful.klas.data.backup

import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.homework.HomeworkEntity
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupIntegrityTest {

    @get:Rule
    val env = TestEnv()

    private val validClass = LegacyClass(
        id = 1, studentId = 1, subjectId = 1,
        startMillis = 1_900_000_000_000, durationMinutes = 60,
        priceMinor = 5000, status = LessonStatus.COMPLETED, chargeMinor = 5000,
        mode = ClassMode.REMOTE,
    )

    private fun backup(
        classes: List<LegacyClass> = listOf(validClass),
        students: List<StudentEntity> = listOf(StudentEntity(1, "Иван")),
        groups: List<GroupEntity> = emptyList(),
        rateHistory: List<RateChangeEntity> = emptyList(),
    ) = BackupData(
        subjects = listOf(SubjectEntity(1, "Алгебра")),
        students = students,
        groups = groups,
        classes = classes,
        rateHistory = rateHistory,
    )

    private fun refusalFor(data: BackupData): MalformedBackupException? =
        runCatching { BackupIntegrity.check(data) }.exceptionOrNull() as? MalformedBackupException

    @Test
    fun `a backup of readable rows passes`() {
        BackupIntegrity.check(backup())
    }

    @Test
    fun `strange but readable data is not refused`() {
        val odd = validClass.copy(
            startMillis = 0, // 1970
            durationMinutes = 1,
            priceMinor = 0,
            chargeMinor = 0,
            status = LessonStatus.SCHEDULED,
        )
        BackupIntegrity.check(backup(classes = listOf(odd)))
    }

    @Test
    fun `a lesson of no length is refused`() {
        assertTrue(refusalFor(backup(classes = listOf(validClass.copy(durationMinutes = 0)))) != null)
        assertTrue(refusalFor(backup(classes = listOf(validClass.copy(durationMinutes = -30)))) != null)
    }

    private fun parseFailure(file: String): Throwable? =
        runCatching { env.backupManager.parse(file) }.exceptionOrNull()

    private fun assertRefusedAsMalformed(file: String, name: String) {
        val error = parseFailure(file)
        assertEquals("refused as a broken file: $error", BackupFailure.Malformed, error?.let(BackupFailure::of))
        assertTrue("says what was wrong: ${error?.message}", error!!.message!!.contains("'$name'"))
    }

    private val legacyClassJson =
        """"id": 1, "studentId": 1, "subjectId": 1, "startMillis": 1900000000000, "durationMinutes": 60"""

    @Test
    fun `an unknown class status is refused, naming it`() {
        assertRefusedAsMalformed(
            """{"version": 5, "classes": [{$legacyClassJson, "status": "MISSED"}]}""",
            name = "MISSED",
        )
    }

    @Test
    fun `an unknown class mode is refused`() {
        assertRefusedAsMalformed(
            """{"version": 5, "classes": [{$legacyClassJson, "status": "COMPLETED", "mode": "HYBRID"}]}""",
            name = "HYBRID",
        )
    }

    @Test
    fun `an unknown student default mode is refused`() {
        assertRefusedAsMalformed(
            """{"students": [{"id": 1, "name": "Иван", "defaultMode": "TELEPATHY"}]}""",
            name = "TELEPATHY",
        )
    }

    @Test
    fun `an unknown group default mode is refused`() {
        assertRefusedAsMalformed(
            """{"groups": [{"id": 1, "name": "11 «А»", "defaultMode": "HYBRID"}]}""",
            name = "HYBRID",
        )
    }

    @Test
    fun `an unknown lesson status is refused, naming it`() {
        assertRefusedAsMalformed(
            """{"lessons": [{"id": 4, "subjectId": 1, "startMillis": 1, "durationMinutes": 60, "status": "MISSED"}]}""",
            name = "MISSED",
        )
    }

    @Test
    fun `an unknown attendance is refused, naming it`() {
        assertRefusedAsMalformed(
            """{"attendance": [{"id": 9, "lessonId": 4, "studentId": 1, "state": "PROBABLY"}]}""",
            name = "PROBABLY",
        )
    }

    @Test
    fun `a near-miss name is refused too`() {
        assertRefusedAsMalformed(
            """{"lessons": [{"id": 4, "subjectId": 1, "startMillis": 1, "durationMinutes": 60, "status": "completed"}]}""",
            name = "completed",
        )
    }

    @Test
    fun `a newer file naming a status this build lacks is refused as newer, not as broken`() {
        val newer = BackupData.CURRENT_VERSION + 1
        val file = """
            {"version": $newer,
             "lessons": [{"id": 4, "subjectId": 1, "startMillis": 1, "durationMinutes": 60, "status": "MISSED"}]}
        """.trimIndent()

        val failure = parseFailure(file)?.let(BackupFailure::of)

        assertEquals(BackupFailure.UnsupportedVersion(newer, BackupData.CURRENT_VERSION), failure)
    }

    @Test
    fun `a supported file with readable names parses`() {
        val parsed = env.backupManager.parse(
            """{"version": 5, "classes": [{$legacyClassJson, "status": "COMPLETED", "mode": "REMOTE"}]}""",
        )

        assertEquals(listOf(validClass.copy(priceMinor = 0, chargeMinor = 0)), parsed.classes)
    }

    @Test
    fun `importing a file naming an unknown status changes nothing`() = runTest {
        env.fixtures.subject("Физика")
        val transactionsBefore = env.transactor.count

        val failure = runCatching {
            env.backupManager.importJson(
                """{"version": 5, "subjects": [{"id": 1, "name": "Алгебра"}],
                   "classes": [{$legacyClassJson, "status": "MISSED"}]}""",
            )
        }

        assertEquals(BackupFailure.Malformed, failure.exceptionOrNull()?.let(BackupFailure::of))
        assertEquals("no transaction was even opened", transactionsBefore, env.transactor.count)
        assertEquals("the tutor's data is untouched", listOf("Физика"), env.stored().subjects.map { it.name })
    }

    @Test
    fun `a v6 lesson of no length is refused`() {
        val data = BackupData(
            lessons = listOf(
                BackupLesson(4, subjectId = 1, startMillis = 1L, durationMinutes = 0, status = LessonStatus.SCHEDULED),
            ),
        )
        assertTrue(refusalFor(data) != null)
    }

    private val taughtLesson =
        BackupLesson(
            4,
            subjectId = 1,
            startMillis = 1L,
            durationMinutes = 60,
            status = LessonStatus.COMPLETED,
            homework = "§12",
        )

    @Test
    fun `a v8 verdict passes whatever it says`() {
        BackupIntegrity.check(
            BackupData(
                version = 8,
                lessons = listOf(taughtLesson),
                attendance = listOf(
                    BackupAttendance(9, 4, 1, state = Attended.PRESENT),
                    BackupAttendance(10, 4, 2, state = Attended.PRESENT, homeworkResult = "EXCELLENT"),
                ),
            ),
        )
    }

    @Test
    fun `homework for neither a student nor a group is refused, naming the row`() {
        val data = BackupData(homework = listOf(HomeworkEntity(id = 3, text = "§12", givenAtMillis = 1L)))

        val refusal = refusalFor(data)

        assertTrue("refused", refusal != null)
        assertTrue("says which row: ${refusal?.reason}", refusal!!.reason.contains("homework 3"))
    }

    @Test
    fun `a rate change belonging to neither a student nor a group is refused`() {
        val orphan = RateChangeEntity(id = 1, toMinor = 5000, atMillis = 1_900_000_000_000)
        assertTrue(refusalFor(backup(rateHistory = listOf(orphan))) != null)
    }

    @Test
    fun `a rate change with an owner passes`() {
        BackupIntegrity.check(
            backup(
                rateHistory = listOf(
                    RateChangeEntity(1, studentId = 1, subjectId = 1, toMinor = 5000, atMillis = 1L),
                    RateChangeEntity(2, groupId = 7, toMinor = 2000, atMillis = null),
                ),
            ),
        )
    }

    @Test
    fun `importing a file with an unreadable row changes nothing`() = runTest {
        env.subjectDao.upsert(SubjectEntity(7, "Физика"))
        val bad = BackupData(
            subjects = listOf(SubjectEntity(1, "Алгебра")),
            students = listOf(StudentEntity(1, "Иван")),
            classes = listOf(validClass.copy(durationMinutes = 0)),
        )
        val transactionsBefore = env.transactor.count

        val failure = runCatching { env.backupManager.import(bad) }

        assertTrue(failure.isFailure)
        assertEquals("no transaction was even opened", transactionsBefore, env.transactor.count)
        assertEquals("the tutor's data is untouched", listOf("Физика"), env.stored().subjects.map { it.name })
    }

    @Test
    fun `an unreadable row classifies as malformed, not as an unreadable file`() {
        val failure = BackupFailure.of(MalformedBackupException("lesson 1 lasts 0 minutes"))

        assertEquals(BackupFailure.Malformed, failure)
    }

    @Test
    fun `a rate too large to bill is refused`() {
        val data = BackupData(classes = listOf(validClass.copy(priceMinor = Money.MAX.minor + 1)))

        val reason = assertThrows(data)
        assertTrue("names the column: $reason", reason.contains("rate"))
    }

    @Test
    fun `a charge too large to add up is refused`() {
        val data = BackupData(classes = listOf(validClass.copy(chargeMinor = Long.MAX_VALUE)))

        assertTrue(assertThrows(data).contains("charge"))
    }

    @Test
    fun `a negative amount is refused`() {
        val data = BackupData(classes = listOf(validClass.copy(priceMinor = -1)))

        assertTrue(assertThrows(data).contains("-1"))
    }

    @Test
    fun `a group rate too large to bill is refused`() {
        val data = BackupData(
            groups = listOf(GroupEntity(id = 1, name = "11 «А»", priceMinor = Money.MAX.minor + 1)),
        )

        assertTrue(assertThrows(data).contains("group 1"))
    }

    @Test
    fun `an ordinary amount passes`() {
        BackupIntegrity.check(
            BackupData(
                classes = listOf(validClass.copy(priceMinor = 3000, chargeMinor = 3000)),
                groups = listOf(GroupEntity(id = 1, name = "11 «А»", priceMinor = 2000)),
            ),
        )
    }

    @Test
    fun `the ceiling itself passes`() {
        BackupIntegrity.check(
            BackupData(classes = listOf(validClass.copy(priceMinor = Money.MAX.minor))),
        )
    }

    private fun assertThrows(data: BackupData): String =
        runCatching { BackupIntegrity.check(data) }
            .exceptionOrNull()
            .let { it as? MalformedBackupException }
            ?.reason
            ?: error("expected the backup to be refused")

    @Test
    fun `parsing a stored enum name is total`() {
        assertEquals(LessonStatus.COMPLETED, LessonStatus.parse("COMPLETED"))
        assertEquals(ClassMode.REMOTE, ClassMode.parse("REMOTE"))
        assertNull(LessonStatus.parse("MISSED"))
        assertNull(LessonStatus.parse("completed"))
        assertNull(LessonStatus.parse(""))
        assertNull(ClassMode.parse("IN PERSON"))
    }

    @Test
    fun `homework for a student and a group at once is refused`() {
        val data = BackupData(
            homework = listOf(HomeworkEntity(id = 4, studentId = 1, groupId = 7, text = "§12", givenAtMillis = 1L)),
        )

        val refusal = refusalFor(data)

        assertTrue("refused", refusal != null)
        assertTrue("says which row: ${refusal?.reason}", refusal!!.reason.contains("homework 4"))
    }
}
