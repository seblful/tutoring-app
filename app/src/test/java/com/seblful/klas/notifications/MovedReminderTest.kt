package com.seblful.klas.notifications

import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class MovedReminderTest {
    @get:Rule
    val env = TestEnv()

    private fun due() = DueReminder(
        env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.homeworkRepo,
        env.notifier, env.settingsRepo, env.clock,
    )

    @Test
    fun `an old alarm does not announce a lesson moved to tomorrow`() = runTest {
        val now = Instant.parse("2026-06-01T10:00:00Z")
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), now.plusSeconds(3600)).single()
        env.classRepo.move(env.classRepo.getSessionFor(id)!!, now.plusSeconds(90000), now)

        assertFalse(due().post(id, now))
        assertTrue(env.notifier.posted.isEmpty())
    }

    @Test
    fun `delivery uses the current configured lead time and includes its boundary`() = runTest {
        val now = Instant.parse("2026-06-01T10:00:00Z")
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), now.plusSeconds(1800)).single()
        env.settingsRepo.update { it.copy(reminderLeadMinutes = 10) }

        assertFalse(due().post(id, now))
        assertTrue(due().post(id, now.plusSeconds(1200)))
    }
}
