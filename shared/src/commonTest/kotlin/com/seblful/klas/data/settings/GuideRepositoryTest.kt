package com.seblful.klas.data.settings

import com.seblful.klas.domain.guide.FirstSteps
import com.seblful.klas.domain.guide.Guide
import com.seblful.klas.domain.guide.Tip
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class GuideRepositoryTest {

    private val env = TestEnv()

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    @Test
    fun `a new install has the whole guide to come`() = runTest {
        assertEquals(Guide.FRESH, env.guideRepo.guide.first())
        assertEquals(FirstSteps.Progress(), env.guideRepo.progress.first())
    }

    @Test
    fun `each first step is read from what has happened`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        env.fixtures.lesson(subject, listOf(student), Instant.parse("2026-06-20T10:00:00Z"))
        assertEquals(FirstSteps.Progress(booked = true), env.guideRepo.progress.first())

        env.fixtures.completedLesson(subject, listOf(student), Instant.parse("2026-06-21T10:00:00Z"))
        env.paymentRepo.record(student, Money.ofMajor(30), LocalDate(2026, 6, 21))
        env.fixtures.homework(Assignee.Student(student))

        assertEquals(
            FirstSteps.Progress(booked = true, taught = true, paid = true, homework = true),
            env.guideRepo.progress.first(),
        )
    }

    @Test
    fun `seen tips and the hidden card are kept, and showing again brings both back`() = runTest {
        env.guideRepo.seen(Tip.LESSON_SWIPE)
        env.guideRepo.seen(Tip.SCHEDULE_DATE)
        env.guideRepo.setFirstStepsHidden(true)
        assertEquals(
            Guide(setOf(Tip.LESSON_SWIPE, Tip.SCHEDULE_DATE), firstStepsHidden = true),
            env.guideRepo.guide.first(),
        )

        env.guideRepo.showAgain()

        assertEquals(Guide.FRESH, env.guideRepo.guide.first())
    }

    @Test
    fun `a restore marks the guide done - whoever restores knows the app`() = runTest {
        env.fixtures.subject()
        env.backupRestore.restore(env.backupManager.exportJson())

        assertEquals(Guide.DONE, env.guideRepo.guide.first())
    }
}
