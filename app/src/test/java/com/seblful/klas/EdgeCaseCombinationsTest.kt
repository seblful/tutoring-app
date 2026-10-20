package com.seblful.klas

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.data.repository.LessonWrite
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.GroupRules
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.schedule.booking.ClassForm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EdgeCaseCombinationsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()
    private val zone: TimeZone = TimeZone.currentSystemDefault()

    @Test
    fun `restoring a cancelled lesson into an hour taken since does not double-book the tutor`() =
        runTest(mainRule.dispatcher) {
            val ann = env.fixtures.student("Аня")
            val bob = env.fixtures.student("Борис")
            val subj = env.fixtures.subject("Математика")
            val at = Clock.System.todayIn(zone).plus(1, DateTimeUnit.DAY).atTime(10, 0).toInstant(zone)

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

            assertTrue(
                "the repository itself refuses the way back into a taken hour",
                outcome is LessonWrite.HourTaken,
            )
            val occupying = Scheduling.conflicts(Scheduling.Slot(at, 60), env.classRepo.getAll())
            assertEquals(
                "the tutor is one person: only one lesson may occupy 10:00",
                1,
                occupying.size,
            )
        }

    @Test
    fun `a subject a group is taught cannot be deleted`() = runTest(mainRule.dispatcher) {
        val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            emptySet(),
        )

        assertEquals(
            "a group is taught this subject, so it is still referenced",
            SubjectRepository.Delete.TaughtToGroup,
            env.subjectRepo.delete(Subject(id = chem, name = "Химия")),
        )
    }

    @Test
    fun `a rate the money field accepts never bills a negative charge`() {
        val absurd = MoneyField(text = "2000000000000000", kind = MoneyField.Kind.RATE)
        assertFalse("an amount that would wrap the charge is not an amount", absurd.isValid)

        val charge = ClassRules.chargeFor(Money.MAX, durationMinutes = 24 * 60)
        assertFalse("the largest accepted rate cannot bill a negative amount", charge.isNegative)
    }

    @Test
    fun `a lesson may cross midnight`() {
        val form = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate(2026, 3, 2),
            timeFrom = LocalTime(23, 30),
            durationMinutes = 60,
            price = MoneyField(text = "30"),
        )
        assertTrue(form.isValid)
        assertEquals(LocalTime(0, 30), form.ends(TimeZone.of("Europe/Minsk"))?.time)
        assertEquals(true, form.ends(TimeZone.of("Europe/Minsk"))?.nextDay)

        val slot = form.slot(TimeZone.of("Europe/Minsk"))!!
        assertEquals(
            "the lesson ends on the 3rd, an hour after it started on the 2nd",
            LocalDateTime(2026, 3, 3, 0, 30),
            slot.end.toLocalDateTime(TimeZone.of("Europe/Minsk")),
        )
    }

    @Test
    fun `saving a group re-staffs it to the students priced for its subject`() =
        runTest(mainRule.dispatcher) {
            val max = env.studentDao.upsert(StudentEntity(name = "Максим"))
            val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
            env.enrollmentRepo.enroll(max, chem, Money.ofMajor(25))
            val groupId = env.groupRepo.save(
                Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
                setOf(max),
            ).id
            env.enrollmentRepo.unenroll(max, chem)

            val group = env.groupRepo.getById(groupId)!!
            env.groupRepo.save(group.copy(name = "Химия, вечер"), env.groupRepo.getMemberIds(groupId).toSet())

            assertEquals(emptyList<Long>(), env.groupRepo.getMemberIds(groupId))
        }

    @Test
    fun `an all-time trend spans as many years as the data does`() {
        val classes = listOf(
            completed(id = 1, at = Instant.fromEpochMilliseconds(0)),
            completed(id = 2, at = Instant.parse("2026-05-01T09:00:00Z")),
        )

        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.ALL, YearMonth(2026, 5)), zone)
        assertEquals(Reports.Trend.Bucket.YEARS, trend.bucket)
        assertTrue("a bar per year since 1970", trend.bars.size > 50)
    }

    @Test
    fun `a group session's length is its first class's, not the sum of its members'`() {
        val at = Instant.parse("2026-03-02T09:00:00Z")
        val totals = Taught.byGroup(
            listOf(
                completed(id = 1, at = at, groupId = 7, studentId = 1, minutes = 60),
                completed(id = 2, at = at, groupId = 7, studentId = 2, minutes = 90),
            ),
        )

        assertEquals(1, totals.lessons)
        assertEquals(60, totals.minutes)
    }

    /** Ticking somebody into a lesson that was called off must not bill them for it. */
    @Test
    fun `a member ticked into a cancelled lesson is not billed for it`() {
        val at = Instant.parse("2026-03-02T09:00:00Z")
        val cancelled = ClassSession(
            groupId = 7,
            classes = listOf(
                TutoringClass(
                    id = 1, studentId = 1, subjectId = 1, start = at, durationMinutes = 60,
                    status = LessonStatus.CANCELLED, groupId = 7,
                ),
            ),
        )

        val change = GroupRules.rosterChange(cancelled, setOf(1L, 2L), edit(subjectId = 1), at)
        val joiner = ClassRules.rebill(change.joining.single())
        assertEquals(LessonStatus.CANCELLED, joiner.status)
        assertTrue("a cancelled lesson bills nobody, joiners included", joiner.charge.isZero)
    }

    /**
     * Clearing every tick on a booked group lesson is refused, and the refusal is an event — the
     * lesson has to survive the attempt rather than the roster rule throwing through the save.
     */
    @Test
    fun `emptying a booked group lesson's roster is refused without touching it`() =
        runTest(mainRule.dispatcher) {
            val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            val groupId = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann)).id
            val at = Instant.parse("2026-03-02T09:00:00Z")
            val ids = env.classRepo.book(
                subjectId = subj, start = at, durationMinutes = 60, mode = ClassMode.IN_PERSON,
                studentIds = listOf(ann), price = Money.ofMajor(20), groupId = groupId,
            ).written()
            val session = env.classRepo.getSessionFor(ids.single())!!

            val refusal = runCatching {
                GroupRules.rosterChange(session, emptySet(), edit(subj), at)
            }.exceptionOrNull()

            assertNotNull("an empty roster is refused", refusal)
            assertEquals("the lesson is untouched", 1, env.classRepo.getAll().size)
        }

    private fun edit(subjectId: Long) = SessionEdit(
        subjectId = subjectId,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        price = Money.ofMajor(20),
    )

    private fun completed(
        id: Long,
        at: Instant,
        groupId: Long? = null,
        studentId: Long = 1,
        minutes: Int = 60,
    ) = TutoringClass(
        id = id,
        studentId = studentId,
        subjectId = 1,
        start = at,
        durationMinutes = minutes,
        price = Money.ofMajor(30),
        status = LessonStatus.COMPLETED,
        charge = ClassRules.chargeFor(Money.ofMajor(30), minutes),
        groupId = groupId,
    )
}
