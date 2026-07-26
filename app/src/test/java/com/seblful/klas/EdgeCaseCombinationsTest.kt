package com.seblful.klas

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.ClassRules
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.ReportPeriod
import com.seblful.klas.domain.Reports
import com.seblful.klas.domain.Scheduling
import com.seblful.klas.domain.SessionEdit
import com.seblful.klas.domain.Taught
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.schedule.ClassForm
import com.seblful.klas.ui.schedule.ScheduleViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Edge cases that combinations of individually-legal data reach. Each one is a *combination*: no
 * single value in it is wrong, and the app was happy with each step on its own.
 *
 * The first three are **regressions**. Each was a live bug found by asking what two ordinary
 * operations do next to each other — a cancelled lesson re-booked and then restored, a group made for
 * a subject that is then deleted, a rate typed larger than the charge arithmetic can hold — and each
 * states the invariant the app claims rather than the mechanics of its fix.
 *
 * The rest pin down behaviour that *looks* like a bug and is in fact decided, so the decision has a
 * test and not only a paragraph of prose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EdgeCaseCombinationsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()
    private val zone: ZoneId = ZoneId.systemDefault()

    // ---- Regressions ---------------------------------------------------------------------------

    /**
     * Cancel Аня's lesson, re-book the freed hour for Борис, then put Аня's lesson back on the
     * schedule. A cancelled class gives its slot back on purpose, so the re-booking is legitimate —
     * which makes un-cancelling the one way back into an hour somebody else now holds. Booking,
     * editing and moving all check for a clash; a status change does not.
     */
    @Test
    fun `restoring a cancelled lesson into an hour taken since does not double-book the tutor`() =
        runTest(mainRule.dispatcher) {
            val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
            val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
            val at = LocalDate.now(zone).plusDays(1).atTime(10, 0).atZone(zone).toInstant()

            val annIds = env.classRepo.book(
                subjectId = subj, start = at, durationMinutes = 60,
                mode = ClassMode.IN_PERSON, studentIds = listOf(ann), price = Money.ofMajor(30),
            )
            env.classRepo.setStatus(
                env.classRepo.getSessionFor(annIds.single())!!,
                ClassStatus.CANCELLED,
            )

            // The hour is free again, so this is an ordinary booking that the form allows.
            env.classRepo.book(
                subjectId = subj, start = at, durationMinutes = 60,
                mode = ClassMode.IN_PERSON, studentIds = listOf(bob), price = Money.ofMajor(30),
            )

            // «Вернуть в расписание» — one tap in the lesson's action sheet.
            val vm = ScheduleViewModel(
                env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, mainRule.appScope,
            )
            vm.setStatus(env.classRepo.getSessionFor(annIds.single())!!, ClassStatus.SCHEDULED)
            advanceUntilIdle()

            val occupying = Scheduling.conflicts(Scheduling.Slot(at, 60), env.classRepo.getAll())
            assertEquals(
                "the tutor is one person: only one lesson may occupy 10:00",
                1,
                occupying.size,
            )
        }

    /**
     * `student_groups.subjectId` is `ON DELETE RESTRICT`, so the database refuses to delete a subject
     * a group is taught. The repository has to refuse it first: it only counts *classes*, so it
     * reports the deletion as done and lets the constraint throw instead — on a real database, out of
     * the `viewModelScope.launch` in `SubjectsViewModel.delete`, where nothing catches it.
     */
    @Test
    fun `a subject a group is taught cannot be deleted`() = runTest(mainRule.dispatcher) {
        val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            emptySet(),
        )

        assertFalse(
            "a group is taught this subject, so it is still referenced",
            env.subjectRepo.delete(Subject(id = chem, name = "Химия")),
        )
    }

    /**
     * A typed rate used to be capped only by what fits in a `Long` of kopecks, while the charge is
     * `rate × minutes` — which wraps well before that. A negative charge then read as income the tutor
     * never earned, in reverse: the student's card showed «Переплата».
     *
     * Fixed at the **input**, which is the only place the problem is still legible: `parseMoneyOrNull`
     * refuses anything above [Money.MAX], a ceiling chosen so the multiplications downstream cannot
     * wrap (see its KDoc). Both halves are pinned here — the field says no, and the largest amount it
     * *does* accept still bills a positive charge over the longest lesson there can be.
     */
    @Test
    fun `a rate the money field accepts never bills a negative charge`() {
        val absurd = MoneyField(text = "2000000000000000", kind = MoneyField.Kind.RATE)
        assertFalse("an amount that would wrap the charge is not an amount", absurd.isValid)

        val charge = ClassRules.chargeFor(Money.MAX, durationMinutes = 24 * 60)
        assertFalse("the largest accepted rate cannot bill a negative amount", charge.isNegative)
    }

    // ---- Decided behaviour, pinned ------------------------------------------------------------

    /**
     * A lesson's times are wall-clock on one date, so 23:30 → 00:30 reads as a negative range and the
     * form says so (`TimeProblem.RangeInvalid`). Picking a late start *creates* that state, since the
     * end follows the start by the lesson's length — so a lesson crossing midnight cannot be booked
     * at all, only shortened to end by 23:59. Documented in `ClassForm`; pinned here because it is
     * the first thing that looks like a bug.
     */
    @Test
    fun `a lesson cannot cross midnight`() {
        val form = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 3, 2),
            timeFrom = LocalTime.of(23, 30),
            timeTo = LocalTime.of(23, 30).plusMinutes(60),
            price = MoneyField(text = "30"),
        )
        assertNull("23:30 + 60 min wraps to 00:30, which is not a range", form.durationMinutes)
        assertFalse(form.isValid)
    }

    /**
     * `GroupRepository.save` re-intersects the roster with the students priced for the group's subject
     * on **every** save, a rename included. So un-enrolling a member from that subject one-to-one
     * (allowed while the group has taught no lesson) makes the next rename drop them from the group.
     * Deliberate — a member who is not priced for the subject could never be billed for its lessons —
     * and the group form does show the shortened candidate list, but nothing says why they left.
     */
    @Test
    fun `saving a group re-staffs it to the students priced for its subject`() =
        runTest(mainRule.dispatcher) {
            val max = env.studentDao.upsert(StudentEntity(name = "Максим"))
            val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
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

    /**
     * An all-time trend enumerates every year between the first and last earning one, and the *data*
     * sets both ends — so one class with a stored start of 0 (which `BackupIntegrity` allows: it
     * checks that a duration is positive and a status readable, not that a date is plausible) draws
     * every year since 1970. Unreachable through the app; a restored file can carry it.
     */
    @Test
    fun `an all-time trend spans as many years as the data does`() {
        val classes = listOf(
            completed(id = 1, at = Instant.EPOCH),
            completed(id = 2, at = Instant.parse("2026-05-01T09:00:00Z")),
        )

        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.ALL, YearMonth.of(2026, 5)), zone)
        assertEquals(Reports.Trend.Bucket.YEARS, trend.bucket)
        assertTrue("a bar per year since 1970", trend.bars.size > 50)
    }

    /**
     * A group lesson is *n* classes that share a duration by construction, so the group card reads it
     * off the session's first class. A restored backup can carry a session whose classes disagree;
     * the answer is then the lowest-id member's, not a sum — which is the safe direction (a group's
     * hours can't be inflated) and the reason this is not a bug.
     */
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
                    status = ClassStatus.CANCELLED, groupId = 7,
                ),
            ),
        )

        val change = GroupRules.rosterChange(cancelled, setOf(1L, 2L), edit(subjectId = 1), at)
        val joiner = ClassRules.rebill(change.joining.single())
        assertEquals(ClassStatus.CANCELLED, joiner.status)
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
            val groupId = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann))
            val at = Instant.parse("2026-03-02T09:00:00Z")
            val ids = env.classRepo.book(
                subjectId = subj, start = at, durationMinutes = 60, mode = ClassMode.IN_PERSON,
                studentIds = listOf(ann), price = Money.ofMajor(20), groupId = groupId,
            )
            val session = env.classRepo.getSessionFor(ids.single())!!

            // What the form's save reaches with every tick cleared — refused before the repository.
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
        status = ClassStatus.COMPLETED,
        charge = ClassRules.chargeFor(Money.ofMajor(30), minutes),
        groupId = groupId,
    )
}
