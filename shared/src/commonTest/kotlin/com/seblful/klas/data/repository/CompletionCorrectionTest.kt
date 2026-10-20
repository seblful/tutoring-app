package com.seblful.klas.data.repository

import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CompletionCorrectionTest {
    private val env = TestEnv()
    private val start = Instant.parse("2026-05-01T10:00:00Z")

    @AfterTest
    fun closeEnvironment() = env.close()

    private suspend fun completeGroup(): ClassSession {
        val subject = env.fixtures.subject()
        val members = (1..3).map { env.fixtures.pricedStudent("Student $it", subject) }
        val group = env.fixtures.group(subjectId = subject, members = members.toSet())
        val ids = env.fixtures.lesson(subject, members, start, groupId = group)
        env.classRepo.setAttendance(env.classRepo.getById(ids[1])!!, Attended.ABSENT)
        env.classRepo.setAttendance(env.classRepo.getById(ids[2])!!, Attended.ABSENT_BILLED)
        env.classRepo.setStatus(env.classRepo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED).written()
        return env.classRepo.getSessionFor(ids.first())!!
    }

    @Test
    fun `undo removes charges and preserves absences and the whole booked roster`() = runTest {
        val completed = completeGroup()
        assertTrue(completed.classes.any { it.charge > Money.ZERO })

        assertTrue(env.classRepo.correctCompletion(completed, unchanged = completed))

        val corrected = env.classRepo.getSessionFor(completed.first.id)!!
        assertEquals(completed.ids, corrected.ids)
        assertEquals(LessonStatus.SCHEDULED, corrected.status)
        assertEquals(
            listOf(Attended.EXPECTED, Attended.ABSENT, Attended.ABSENT_BILLED),
            corrected.classes.map {
                it.attended
            },
        )
        assertTrue(corrected.classes.all { it.charge == Money.ZERO })
        assertTrue(env.stored().payments.isEmpty())
    }

    @Test
    fun `an old undo receipt cannot overwrite a later rate or mode correction`() = runTest {
        val receipt = completeGroup()
        env.classRepo.apply(
            receipt, SessionEdit(receipt.first.subjectId, 90, ClassMode.REMOTE, Money.ofMajor(40)),
            start, now = env.clock.now(),
        ).written()
        val before = env.stored()

        assertFalse(env.classRepo.correctCompletion(receipt, unchanged = receipt))
        assertEquals(before, env.stored())
        // The later explicit action works on current facts without restoring the receipt's old rate.
        assertTrue(env.classRepo.correctCompletion(receipt))
        val corrected = env.classRepo.getSessionFor(receipt.first.id)!!
        assertTrue(corrected.classes.all { it.price == Money.ofMajor(40) && it.mode == ClassMode.REMOTE })
    }

    @Test
    fun `undo cannot revive a subsequently cancelled or deleted lesson`() = runTest {
        val receipt = completeGroup()
        env.classRepo.setStatus(receipt, LessonStatus.CANCELLED).written()
        assertFalse(env.classRepo.correctCompletion(receipt, unchanged = receipt))
        assertEquals(LessonStatus.CANCELLED, env.classRepo.getSessionFor(receipt.first.id)!!.status)
        env.classRepo.delete(receipt)
        assertFalse(env.classRepo.correctCompletion(receipt, unchanged = receipt))
        assertTrue(env.classRepo.getAll().isEmpty())
    }
}
