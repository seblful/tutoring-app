package com.seblful.klas.data.backup

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.HomeworkEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A backup whose rows cannot be read back is refused **before** anything is written.
 *
 * The bug these are about: the import writes entity rows verbatim, and the text columns holding an
 * enum were only interpreted later, on the read path, inside the `Flow`s the screens collect. A file
 * with an unknown status therefore imported cleanly and then broke every screen that lists a lesson —
 * permanently, because the bad row was now the saved data, and silently, because a dead collector
 * draws nothing rather than reporting anything. Those columns are typed now, so an unknown name is
 * refused as the file is parsed; what is left for [BackupIntegrity] is what a type cannot say (a length,
 * an amount, an owner).
 *
 * Most of these describe the **v1–v5 flat class** ([LegacyClass]), which is where a file this build has
 * to refuse is most likely to come from — those files are the ones already written. They are checked
 * through the same split that would import them, so the two shapes cannot disagree about what is
 * readable; the v6 rows are checked directly, below.
 */
class BackupIntegrityTest {

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

    // --- What passes ------------------------------------------------------------------------------

    @Test
    fun `a backup of readable rows passes`() {
        BackupIntegrity.check(backup())
    }

    /**
     * The check is about readability, not plausibility. A backup is allowed to be strange — the tutor's
     * data is theirs — so nothing here may start refusing lessons at odd hours or unpriced rates.
     */
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

    // --- What is refused --------------------------------------------------------------------------

    /**
     * A lesson of zero or negative length ends at or before it starts, so `Slot.overlaps` matches
     * nothing and the one-lesson-at-a-time rule silently stops holding. Worth refusing at the door
     * rather than discovering as a double-booked evening.
     */
    @Test
    fun `a lesson of no length is refused`() {
        assertTrue(refusalFor(backup(classes = listOf(validClass.copy(durationMinutes = 0)))) != null)
        assertTrue(refusalFor(backup(classes = listOf(validClass.copy(durationMinutes = -30)))) != null)
    }

    // --- An unknown enum name: refused as the file is parsed ---------------------------------------
    //
    // The rows' enum columns are typed, so a name no constant has cannot become a row at all, and the
    // check above never sees one. What these pin is that the file is still refused — before anything is
    // written, as the same failure the tutor was shown when the check found it — and that the refusal
    // still says what was wrong. (Not *which row*: the decoder names the value and its enum, not the id
    // the check used to quote. The tutor never saw either — `BackupFailure.Malformed` carries no text.)

    private fun parseFailure(file: String): Throwable? =
        runCatching { TestEnv().backupManager.parse(file) }.exceptionOrNull()

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

    /**
     * The half the old shape could not carry, and so the half a hand-edited v6 file is likeliest to get
     * wrong: an attendance the app cannot read is a class it cannot decide whether to bill.
     */
    @Test
    fun `an unknown attendance is refused, naming it`() {
        assertRefusedAsMalformed(
            """{"attendance": [{"id": 9, "lessonId": 4, "studentId": 1, "state": "PROBABLY"}]}""",
            name = "PROBABLY",
        )
    }

    /** Case matters, as it does to `LessonStatus.parse`: no build ever wrote a lower-case name. */
    @Test
    fun `a near-miss name is refused too`() {
        assertRefusedAsMalformed(
            """{"lessons": [{"id": 4, "subjectId": 1, "startMillis": 1, "durationMinutes": 60, "status": "completed"}]}""",
            name = "completed",
        )
    }

    /**
     * Why the version is read before the rows. A later build that adds a status writes it into its files;
     * decoding the rows first would call such a file broken, where the truth — and the advice the tutor
     * needs — is that it is newer than this app.
     */
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

    /** A file of this build's version, or older, is parsed whole — the version only refuses what is newer. */
    @Test
    fun `a supported file with readable names parses`() {
        val parsed = TestEnv().backupManager.parse(
            """{"version": 5, "classes": [{$legacyClassJson, "status": "COMPLETED", "mode": "REMOTE"}]}""",
        )

        assertEquals(listOf(validClass.copy(priceMinor = 0, chargeMinor = 0)), parsed.classes)
    }

    @Test
    fun `importing a file naming an unknown status changes nothing`() = runTest {
        val env = TestEnv()
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
        assertEquals("the tutor's data is untouched", listOf("Физика"), env.subjectDao.items.value.map { it.name })
    }

    // --- The v6 rows, checked as themselves --------------------------------------------------------

    @Test
    fun `a v6 lesson of no length is refused`() {
        val data = BackupData(
            lessons = listOf(
                LessonEntity(4, subjectId = 1, startMillis = 1L, durationMinutes = 0, status = LessonStatus.SCHEDULED),
            ),
        )
        assertTrue(refusalFor(data) != null)
    }

    // --- Homework ---------------------------------------------------------------------------------

    private val taughtLesson =
        LessonEntity(4, subjectId = 1, startMillis = 1L, durationMinutes = 60, status = LessonStatus.COMPLETED, homework = "§12")

    /**
     * A v8 file's verdict is only read as "it had one" — done, now — so a name this build never had cannot
     * fail anything, and is no reason to refuse the file.
     */
    @Test
    fun `a v8 verdict passes whatever it says`() {
        BackupIntegrity.check(
            BackupData(
                version = 8,
                lessons = listOf(taughtLesson),
                attendance = listOf(
                    AttendanceEntity(9, 4, 1, state = Attended.PRESENT),
                    AttendanceEntity(10, 4, 2, state = Attended.PRESENT, homeworkResult = "EXCELLENT"),
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

    // --- Through the manager: refused before anything is written -----------------------------------

    /**
     * The whole point of checking before the transaction rather than relying on it: the existing data
     * survives, and the refusal keeps its reason instead of becoming whatever the first bad row threw.
     */
    @Test
    fun `importing a file with an unreadable row changes nothing`() = runTest {
        val env = TestEnv()
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
        assertEquals("the tutor's data is untouched", listOf("Физика"), env.subjectDao.items.value.map { it.name })
    }

    /**
     * And it reaches the tutor as «this file isn't a Klas backup» rather than as «couldn't open the
     * file», which is the advice that used to be given for it — a file that opened perfectly.
     */
    @Test
    fun `an unreadable row classifies as malformed, not as an unreadable file`() {
        val failure = BackupFailure.of(MalformedBackupException("lesson 1 lasts 0 minutes"))

        assertEquals(BackupFailure.Malformed, failure)
    }

    // --- Amounts the money arithmetic has to survive ---------------------------------------------
    //
    // A rate is a bare `Long` of kopecks, so nothing about it fails to *load* — what fails is
    // `rate × minutes`, which wraps past Long.MAX_VALUE and bills a negative charge. The typed-input
    // gate (`parseMoneyOrNull`) holds every amount to `Money.MAX`; a file is the other way in.

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
        // The money fields reject one, so a stored negative can only be a hand-edit or corruption.
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

    /** The largest accepted amount is accepted — the bound is inclusive, not merely near. */
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

    // --- The parse the whole thing rests on -------------------------------------------------------

    @Test
    fun `parsing a stored enum name is total`() {
        assertEquals(LessonStatus.COMPLETED, LessonStatus.parse("COMPLETED"))
        assertEquals(ClassMode.REMOTE, ClassMode.parse("REMOTE"))
        // No throw, unlike valueOf — including for the near misses a hand-edit produces.
        assertNull(LessonStatus.parse("MISSED"))
        assertNull(LessonStatus.parse("completed"))
        assertNull(LessonStatus.parse(""))
        assertNull(ClassMode.parse("IN PERSON"))
    }

    /** Both owners would read back as the student's, taking it from the group without a word. */
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
