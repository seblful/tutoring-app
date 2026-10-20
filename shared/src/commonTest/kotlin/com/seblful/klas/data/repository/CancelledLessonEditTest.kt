package com.seblful.klas.data.repository

import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CancelledLessonEditTest {
    private val env = TestEnv()
    private val start = Instant.parse("2026-05-01T10:00:00Z")

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    @Test
    fun `a cancelled past lesson can be corrected after its hour was reused`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.cancelledLesson(subject, listOf(student), start).single()
        env.fixtures.lesson(subject, listOf(student), start)

        val result = env.classRepo.apply(
            env.classRepo.getSessionFor(id)!!,
            SessionEdit(subject, 90, ClassMode.REMOTE, Money.ofMajor(40)),
            start,
            now = env.clock.now(),
        )

        assertIs<LessonWrite.Written<*>>(result)
        val corrected = env.classRepo.getById(id)!!
        assertEquals(LessonStatus.CANCELLED, corrected.status)
        assertEquals(Money.ZERO, corrected.charge)
        assertEquals(ClassMode.REMOTE, corrected.mode)
        assertEquals(90, corrected.durationMinutes)
    }

    @Test
    fun `a cancelled lesson can move within occupied past time`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.cancelledLesson(subject, listOf(student), start).single()
        env.fixtures.lesson(subject, listOf(student), start + 2.hours)

        val result = env.classRepo.move(env.classRepo.getSessionFor(id)!!, start + 2.hours, env.clock.now())

        assertIs<LessonWrite.Written<*>>(result)
        assertEquals(start + 2.hours, env.classRepo.getById(id)!!.start)
        assertEquals(LessonStatus.CANCELLED, env.classRepo.getById(id)!!.status)
    }

    @Test
    fun `moving a cancelled lesson to occupied future time is still refused`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.cancelledLesson(subject, listOf(student), start).single()
        val future = env.clock.now() + 24.hours
        env.fixtures.lesson(subject, listOf(student), future)

        val result = env.classRepo.move(env.classRepo.getSessionFor(id)!!, future, env.clock.now())

        assertIs<LessonWrite.HourTaken>(result)
        assertEquals(start, env.classRepo.getById(id)!!.start)
    }
}
