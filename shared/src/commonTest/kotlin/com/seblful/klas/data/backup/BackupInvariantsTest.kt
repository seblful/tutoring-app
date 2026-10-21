package com.seblful.klas.data.backup

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class BackupInvariantsTest {
    @Test
    fun `generated mixed histories round trip without losing rows ids or balances`() = runTest {
        for (seed in 0..11) {
            TestEnv().use { env ->
                val random = Random(seed)
                val subjects = List(2) { env.fixtures.subject("Subject $it") }
                val students = List(4) { env.fixtures.pricedStudent("Student $it", subjects.first()) }
                students.forEach { env.enrollmentRepo.enroll(it, subjects.last(), Money(random.nextLong(1, 10_001))) }
                val group = env.fixtures.group(members = students.take(3).toSet(), subjectId = subjects.first())
                repeat(12) { case ->
                    val grouped = case % 3 == 0
                    val ids = env.fixtures.lesson(
                        subjectId = if (grouped) subjects.first() else subjects.random(random),
                        students = if (grouped) students.take(3) else
                            listOf(students.random(random)),
                        start = env.clock.now() - (case + 1).days, minutes = random.nextInt(1, 181),
                        rate = Money(random.nextLong(Money.MAX.minor + 1)), groupId = if (grouped) group else null,
                    )
                    val status = LessonStatus.entries.random(random)
                    if (status != LessonStatus.SCHEDULED) env.fixtures.setStatus(ids.first(), status)
                    if (status == LessonStatus.COMPLETED) {
                        ids.forEach {
                            env.fixtures.setAttendance(
                                it,
                                listOf(
                                    Attended.PRESENT, Attended.ABSENT,
                                    Attended.ABSENT_BILLED,
                                ).random(random),
                            )
                        }
                    }
                }
                students.forEach { student ->
                    env.paymentRepo.restore(
                        Payment(
                            studentId = student, date = env.clock.now(),
                            amount = Money(random.nextLong(1, Money.MAX.minor + 1)),
                        ),
                    )
                    val homework = env.homeworkRepo.give(Assignee.Student(student), "Exercises $student")
                    env.homeworkRepo.setDone(homework, done = random.nextBoolean())
                }
                env.homeworkRepo.give(Assignee.Group(group), "Group exercises")
                val before = env.stored()
                val balances = env.balanceRepo.get()
                val exported = env.backupManager.exportJson()
                TestEnv().use { target ->
                    target.fixtures.student("Stale student", id = 9000)
                    target.fixtures.subject("Stale subject", id = 9000)
                    target.settingsRepo.update { it.copy(defaultLessonMinutes = 45) }
                    val settings = target.settingsRepo.get()

                    target.backupManager.importJson(exported)

                    assertEquals(before, target.stored(), "seed=$seed rows and IDs replace the target")
                    assertEquals(balances, target.balanceRepo.get(), "seed=$seed balances")
                    assertEquals(settings, target.settingsRepo.get(), "seed=$seed device settings")
                }
            }
        }
    }
}
