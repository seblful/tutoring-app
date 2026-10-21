package com.seblful.klas.data.repository

import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BookingIntegrityTest {
    private val env = TestEnv()
    private val zone = env.clock.zone

    @AfterTest
    fun close() = env.close()

    @Test
    fun `restoring a cancelled lesson into an hour taken since does not double-book the tutor`() =
        runTest {
            val ann = env.fixtures.student("Аня")
            val bob = env.fixtures.student("Борис")
            val subj = env.fixtures.subject("Математика")
            val at = env.clock.today().plus(1, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone)

            val annIds = env.classRepo.book(
                subjectId = subj, start = at, durationMinutes = 60,
                mode = ClassMode.IN_PERSON, studentIds = listOf(ann), price = Money.ofMajor(30),
            ).written()
            env.classRepo.setStatus(
                env.classRepo.getSessionFor(annIds.single())!!,
                LessonStatus.CANCELLED,
            )

            env.classRepo.book(
                subjectId = subj, start = at, durationMinutes = 60,
                mode = ClassMode.IN_PERSON, studentIds = listOf(bob), price = Money.ofMajor(30),
            ).written()

            val outcome = env.classRepo.setStatus(
                env.classRepo.getSessionFor(annIds.single())!!,
                LessonStatus.SCHEDULED,
            )

            assertTrue(outcome is LessonWrite.HourTaken, "the repository itself refuses the way back into a taken hour")
            val occupying = Scheduling.conflicts(Scheduling.Slot(at, 60), env.classRepo.getAll())
            assertEquals(1, occupying.size, "the tutor is one person: only one lesson may occupy 10:00")
        }

    @Test
    fun `saving a group re-staffs it to the students priced for its subject`() =
        runTest {
            val max = env.fixtures.student(name = "Максим")
            val chem = env.fixtures.subject(name = "Химия")
            env.enrollmentRepo.enroll(max, chem, Money.ofMajor(25))
            val groupId = env.groupRepo.save(
                Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
                setOf(max),
            )
            env.enrollmentRepo.unenroll(max, chem)

            val group = env.groupRepo.getById(groupId)!!
            env.groupRepo.save(group.copy(name = "Химия, вечер"), env.groupRepo.getMemberIds(groupId).toSet())

            assertEquals(emptyList<Long>(), env.groupRepo.getMemberIds(groupId))
        }
}
