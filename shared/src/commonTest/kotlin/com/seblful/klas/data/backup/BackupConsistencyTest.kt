package com.seblful.klas.data.backup

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class BackupConsistencyTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }
    val env = TestEnv()

    @Test
    fun `a whole export is read under one transaction`() = runTest {
        val before = env.transactor.count

        env.backupManager.export()

        assertEquals(1, env.transactor.count - before)
    }

    @Test
    fun `a failed snapshot transaction never reports a successful export`() = runTest {
        env.transactor.failAt = env.transactor.count + 1

        assertTrue(runCatching { env.backupManager.exportJson() }.isFailure)
    }

    @Test
    fun `a valid maximum rate and duration-scaled charge round trip`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject, rate = Money.MAX)
        val id = env.fixtures.completedLesson(
            subject, listOf(student), Instant.parse("2026-06-01T10:00:00Z"), minutes = 90, rate = Money.MAX,
        ).single()
        val charge = env.classRepo.getById(id)!!.charge
        assertTrue(charge > Money.MAX)

        env.backupManager.importJson(env.backupManager.exportJson())

        assertEquals(charge, env.classRepo.getById(id)!!.charge)
    }

    @Test
    fun `a valid legacy charge may also exceed the typed rate ceiling`() {
        BackupIntegrity.check(
            BackupData(
                version = 5,
                classes = listOf(
                    LegacyClass(
                        id = 1, studentId = 1, subjectId = 1, startMillis = 1, durationMinutes = 90,
                        status = LessonStatus.COMPLETED, priceMinor = Money.MAX.minor,
                        chargeMinor = ClassRules.chargeFor(Money.MAX, 90).minor,
                    ),
                ),
            ),
        )
    }
}
