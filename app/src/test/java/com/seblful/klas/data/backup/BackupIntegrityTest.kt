package com.seblful.klas.data.backup

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
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
 * enum are only interpreted later, on the read path, inside the `Flow`s the screens collect. A file
 * with an unknown status therefore imported cleanly and then broke every screen that lists a lesson —
 * permanently, because the bad row was now the saved data, and silently, because a dead collector
 * draws nothing rather than reporting anything.
 */
class BackupIntegrityTest {

    private val validClass = ClassEntity(
        id = 1, studentId = 1, subjectId = 1,
        startMillis = 1_900_000_000_000, durationMinutes = 60,
        priceMinor = 5000, status = ClassStatus.COMPLETED.name, chargeMinor = 5000,
        mode = ClassMode.REMOTE.name,
    )

    private fun backup(
        classes: List<ClassEntity> = listOf(validClass),
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
            status = ClassStatus.SCHEDULED.name,
        )
        BackupIntegrity.check(backup(classes = listOf(odd)))
    }

    // --- What is refused --------------------------------------------------------------------------

    @Test
    fun `an unknown class status is refused, naming the row`() {
        val refusal = refusalFor(backup(classes = listOf(validClass.copy(status = "MISSED"))))

        assertTrue("refused", refusal != null)
        assertTrue("says which row: ${refusal?.reason}", refusal!!.reason.contains("class 1"))
        assertTrue("and what was wrong with it", refusal.reason.contains("MISSED"))
    }

    @Test
    fun `an unknown class mode is refused`() {
        assertTrue(refusalFor(backup(classes = listOf(validClass.copy(mode = "HYBRID")))) != null)
    }

    @Test
    fun `an unknown student default mode is refused`() {
        val students = listOf(StudentEntity(1, "Иван", defaultMode = "TELEPATHY"))
        assertTrue(refusalFor(backup(students = students)) != null)
    }

    @Test
    fun `an unknown group default mode is refused`() {
        val groups = listOf(GroupEntity(1, "11 «А»", defaultMode = "HYBRID"))
        assertTrue(refusalFor(backup(groups = groups)) != null)
    }

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
        env.subjectDao.insertAll(listOf(SubjectEntity(7, "Физика")))
        val bad = BackupData(
            subjects = listOf(SubjectEntity(1, "Алгебра")),
            students = listOf(StudentEntity(1, "Иван")),
            classes = listOf(validClass.copy(status = "MISSED")),
        )
        val transactionsBefore = env.transactor.count

        val failure = runCatching { env.backupManager.import(bad) }

        assertTrue(failure.isFailure)
        assertEquals("no transaction was even opened", transactionsBefore, env.transactor.count)
        assertEquals("the tutor's data is untouched", listOf("Физика"), env.subjectDao.getAll().map { it.name })
    }

    /**
     * And it reaches the tutor as «this file isn't a Klas backup» rather than as «couldn't open the
     * file», which is the advice that used to be given for it — a file that opened perfectly.
     */
    @Test
    fun `an unreadable row classifies as malformed, not as an unreadable file`() {
        val failure = BackupFailure.of(MalformedBackupException("class 1 has status \"MISSED\""))

        assertEquals(BackupFailure.Malformed, failure)
    }

    // --- The parse the whole thing rests on -------------------------------------------------------

    @Test
    fun `parsing a stored enum name is total`() {
        assertEquals(ClassStatus.COMPLETED, ClassStatus.parse("COMPLETED"))
        assertEquals(ClassMode.REMOTE, ClassMode.parse("REMOTE"))
        // No throw, unlike valueOf — including for the near misses a hand-edit produces.
        assertNull(ClassStatus.parse("MISSED"))
        assertNull(ClassStatus.parse("completed"))
        assertNull(ClassStatus.parse(""))
        assertNull(ClassMode.parse("IN PERSON"))
    }
}
