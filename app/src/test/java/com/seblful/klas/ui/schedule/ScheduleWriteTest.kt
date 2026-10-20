package com.seblful.klas.ui.schedule

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.lesson.DeleteWarning
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleWriteTest : ScheduleTestFixture() {

    @Test
    fun `setStatus completes a class and captures its charge`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.fixtures.storedClass(
            studentId = sid,
            subjectId = subj,
            startMillis = 1,
            durationMinutes = 90,
            priceMinor = Money.ofMajor(30).minor,
            status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.COMPLETED)
        advanceUntilIdle()

        val saved = env.classes().single { it.id == id }
        assertEquals(LessonStatus.COMPLETED, saved.status)
        assertEquals(Money.ofMajor(45).minor, saved.chargeMinor) // 30/h * 90 min
    }

    @Test
    fun `a lesson cannot be moved onto an hour another lesson already holds`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate(2026, 7, 15)
        val moving = env.fixtures.storedClass(
            studentId = ann,
            subjectId = subj,
            startMillis = startMillisOn(day, LocalTime(9, 0)),
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
        )
        env.fixtures.storedClass(
            studentId = bob,
            subjectId = subj,
            startMillis = startMillisOn(day, LocalTime(15, 0)),
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        vm.moveTo(env.classRepo.getSessionFor(moving)!!, day, LocalTime(15, 30))
        advanceUntilIdle()

        assertEquals("Борис", vm.timeConflict.value?.who)
        assertEquals(
            "the lesson stayed where it was",
            startMillisOn(day, LocalTime(9, 0)),
            env.classes().single { it.id == moving }.startMillis,
        )

        vm.dismissTimeConflict()
        assertNull(vm.timeConflict.value)
    }

    @Test
    fun `a cancelled lesson cannot be returned to an hour taken since`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate(2026, 7, 15)
        val at = startMillisOn(day, LocalTime(10, 0))
        val cancelled = env.fixtures.storedClass(
            studentId = ann,
            subjectId = subj,
            startMillis = at,
            durationMinutes = 60,
            status = LessonStatus.CANCELLED,
        )
        env.fixtures.storedClass(
            studentId = bob,
            subjectId = subj,
            startMillis = at,
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(cancelled)!!, LessonStatus.SCHEDULED)
        advanceUntilIdle()

        assertEquals("Борис", vm.timeConflict.value?.who)
        assertEquals(
            "the lesson stayed cancelled",
            LessonStatus.CANCELLED,
            env.classes().single { it.id == cancelled }.status,
        )
    }

    @Test
    fun `a cancelled lesson returns to a free hour`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate(2026, 7, 15)
        val cancelled = env.fixtures.storedClass(
            studentId = ann, subjectId = subj,
            startMillis = startMillisOn(day, LocalTime(10, 0)),
            durationMinutes = 60, status = LessonStatus.CANCELLED,
        )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(cancelled)!!, LessonStatus.SCHEDULED)
        advanceUntilIdle()

        assertNull("nothing holds the hour, so nothing to refuse", vm.timeConflict.value)
        assertEquals(LessonStatus.SCHEDULED, env.classes().single().status)
    }

    @Test
    fun `a status change that fails to commit is reported`() = runTest(mainRule.dispatcher) {
        val env = TestEnv()
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.fixtures.storedClass(
            studentId = ann, subjectId = subj,
            startMillis = startMillisOn(LocalDate(2026, 7, 15), LocalTime(10, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
        )
        val vm = ScheduleViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.homeworkRepo, mainRule.appScope,
            mainRule.dispatcher, WallClock.system(),
        )
        val seen = mutableListOf<ScheduleViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }

        env.transactor.failAt = env.transactor.count + 1
        vm.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.COMPLETED)
        advanceUntilIdle()

        assertEquals(listOf(ScheduleViewModel.Event.ACTION_FAILED), seen)
    }

    @Test
    fun `a delete that fails to commit is reported`() = runTest(mainRule.dispatcher) {
        val env = TestEnv()
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.fixtures.storedClass(
            studentId = ann, subjectId = subj,
            startMillis = startMillisOn(LocalDate(2026, 7, 15), LocalTime(10, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
        )
        val vm = ScheduleViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.homeworkRepo, mainRule.appScope,
            mainRule.dispatcher, WallClock.system(),
        )
        val seen = mutableListOf<ScheduleViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }

        env.transactor.failAt = env.transactor.count + 1
        vm.delete(env.classRepo.getSessionFor(id)!!)
        advanceUntilIdle()

        assertEquals(listOf(ScheduleViewModel.Event.ACTION_FAILED), seen)
    }

    @Test
    fun `a write that succeeds reports nothing`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.fixtures.storedClass(
            studentId = ann, subjectId = subj,
            startMillis = startMillisOn(LocalDate(2026, 7, 15), LocalTime(10, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
        )
        val vm = vm()
        val seen = mutableListOf<ScheduleViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }

        vm.delete(env.classRepo.getSessionFor(id)!!)
        advanceUntilIdle()

        assertTrue("nothing failed, so nothing to announce", seen.isEmpty())
    }

    @Test
    fun `marking a lesson completed is never refused`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.fixtures.storedClass(
            studentId = ann, subjectId = subj,
            startMillis = startMillisOn(LocalDate(2026, 7, 15), LocalTime(10, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED, priceMinor = 3000,
        )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.COMPLETED)
        advanceUntilIdle()

        assertNull(vm.timeConflict.value)
        assertEquals(LessonStatus.COMPLETED, env.classes().single().status)
    }

    @Test
    fun `a lesson can be nudged within its own hour`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate(2026, 7, 15)
        val id = env.fixtures.storedClass(
            studentId = ann,
            subjectId = subj,
            startMillis = startMillisOn(day, LocalTime(9, 0)),
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        vm.moveTo(env.classRepo.getSessionFor(id)!!, day, LocalTime(9, 10))
        advanceUntilIdle()

        assertNull(vm.timeConflict.value)
        assertEquals(
            startMillisOn(day, LocalTime(9, 10)),
            env.classes().single().startMillis,
        )
    }

    @Test
    fun `a group lesson moves as one, without clashing with its own members`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob)).id
        val day = LocalDate(2026, 7, 15)
        val at = startMillisOn(day, LocalTime(12, 0))
        val first = env.fixtures.storedClass(
            studentId = ann,
            subjectId = subj,
            startMillis = at,
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
            groupId = groupId,
        )
        env.fixtures.storedClass(
            studentId = bob,
            subjectId = subj,
            startMillis = at,
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
            groupId = groupId,
        )

        val vm = vm()
        vm.moveTo(env.classRepo.getSessionFor(first)!!, day, LocalTime(12, 30))
        advanceUntilIdle()

        assertNull(vm.timeConflict.value)
        val moved = startMillisOn(day, LocalTime(12, 30))
        assertEquals(
            "both members moved together",
            listOf(moved, moved),
            env.classes().map { it.startMillis },
        )
    }

    @Test
    fun `completing a group row completes every member's class`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob)).id
        val at = startMillisOn(Clock.System.todayIn(zone), LocalTime(12, 0))
        env.fixtures.storedClass(
            studentId = ann,
            subjectId = subj,
            startMillis = at,
            durationMinutes = 60,
            priceMinor = Money.ofMajor(30).minor,
            status = LessonStatus.SCHEDULED,
            groupId = groupId,
        )
        env.fixtures.storedClass(
            studentId = bob,
            subjectId = subj,
            startMillis = at,
            durationMinutes = 60,
            priceMinor = Money.ofMajor(20).minor,
            status = LessonStatus.SCHEDULED,
            groupId = groupId,
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStatus(vm.dayContent.rows.single().session, LessonStatus.COMPLETED)
        advanceUntilIdle()

        val saved = env.classes()
        assertTrue(saved.all { it.status == LessonStatus.COMPLETED })
        // Each member billed at their own rate.
        assertEquals(
            mapOf(ann to Money.ofMajor(30).minor, bob to Money.ofMajor(20).minor),
            saved.associate { it.studentId to it.chargeMinor },
        )
    }

    @Test
    fun `delete removes the class`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.fixtures.storedClass(
            studentId = sid,
            subjectId = subj,
            startMillis = 1,
            durationMinutes = 60,
            status = LessonStatus.CANCELLED,
        )

        val vm = vm()
        vm.delete(env.classRepo.getSessionFor(id)!!)
        advanceUntilIdle()

        assertTrue(env.classes().isEmpty())
    }

    /**
     * Deleting a lesson owes one of four warnings, and both facts that pick it — has anything billed, is
     * it a group's — used to be read off the session inside a composable. Losing recognized income is the
     * half that matters: it is money the tutor has counted, and nothing else in the app takes it back.
     */
    @Test
    fun `the delete warning says whether income is at stake and how many students`() =
        runTest(mainRule.dispatcher) {
            val subject = env.fixtures.subject("Математика")
            val anya = env.fixtures.pricedStudent("Аня", subject)
            val boris = env.fixtures.pricedStudent("Борис", subject)
            val group = env.fixtures.group("11 «А»", subjectId = subject, members = setOf(anya, boris))

            val scheduled = env.fixtures.lesson(subject, listOf(anya), at(9))
            val completed = env.fixtures.completedLesson(subject, listOf(anya), at(10))
            val groupScheduled = env.fixtures.lesson(subject, listOf(anya, boris), at(11), groupId = group)
            val groupCompleted =
                env.fixtures.completedLesson(subject, listOf(anya, boris), at(12), groupId = group)

            suspend fun warningFor(ids: List<Long>) =
                DeleteWarning.of(env.classRepo.getSessionFor(ids.first())!!)

            assertEquals(DeleteWarning(DeleteWarning.Kind.PLAIN, classes = 1), warningFor(scheduled))
            assertEquals(DeleteWarning(DeleteWarning.Kind.BILLED, classes = 1), warningFor(completed))
            assertEquals(
                "a group lesson is several students' classes at once",
                DeleteWarning(DeleteWarning.Kind.GROUP, classes = 2),
                warningFor(groupScheduled),
            )
            assertEquals(
                DeleteWarning(DeleteWarning.Kind.BILLED_GROUP, classes = 2),
                warningFor(groupCompleted),
            )
        }

    @Test
    fun `a completed lesson nobody was billed for does not warn about income`() =
        runTest(mainRule.dispatcher) {
            val subject = env.fixtures.subject("Математика")
            val anya = env.fixtures.pricedStudent("Аня", subject)
            val boris = env.fixtures.pricedStudent("Борис", subject)
            val group = env.fixtures.group("11 «А»", subjectId = subject, members = setOf(anya, boris))

            val alone = env.fixtures.completedLesson(subject, listOf(anya), at(9))
            env.fixtures.setAttendance(alone.single(), Attended.ABSENT)
            val nobody = env.fixtures.completedLesson(subject, listOf(anya, boris), at(10), groupId = group)
            nobody.forEach { env.fixtures.setAttendance(it, Attended.ABSENT) }
            val someone = env.fixtures.completedLesson(subject, listOf(anya, boris), at(11), groupId = group)
            env.fixtures.setAttendance(someone.first(), Attended.ABSENT)

            suspend fun warningFor(ids: List<Long>) =
                DeleteWarning.of(env.classRepo.getSessionFor(ids.first())!!)

            assertEquals(DeleteWarning(DeleteWarning.Kind.PLAIN, classes = 1), warningFor(alone))
            assertEquals(DeleteWarning(DeleteWarning.Kind.GROUP, classes = 2), warningFor(nobody))
            assertEquals(DeleteWarning(DeleteWarning.Kind.BILLED_GROUP, classes = 2), warningFor(someone))
        }

    @Test
    fun `the row answers what is legal — segments, the swipe, and receding`() =
        runTest(mainRule.dispatcher) {
            val subject = env.fixtures.subject("Математика")
            val anya = env.fixtures.pricedStudent("Аня", subject)
            val boris = env.fixtures.pricedStudent("Борис", subject)
            val group = env.fixtures.group("11 «А»", subjectId = subject, members = setOf(anya, boris))

            val scheduled = env.fixtures.lesson(subject, listOf(anya), at(9))
            val completed = env.fixtures.completedLesson(subject, listOf(anya), at(10))
            val cancelled = env.fixtures.cancelledLesson(subject, listOf(anya), at(11))
            val mixed = env.fixtures.lesson(subject, listOf(anya, boris), at(12), groupId = group)
            val borisClass = env.classRepo.getSessionFor(mixed.first())!!
                .classes.single { it.studentId == boris }
            env.classRepo.setAttendance(borisClass, Attended.ABSENT)

            suspend fun rowFor(ids: List<Long>) =
                ScheduleRow(session = env.classRepo.getSessionFor(ids.first())!!, title = "", subjectName = "")

            val plain = rowFor(scheduled)
            assertTrue("a scheduled lesson can be swiped done", plain.canSwipeComplete)
            assertEquals(setOf(LessonStatus.COMPLETED, LessonStatus.CANCELLED), plain.statusChoices)
            assertFalse(plain.recedes)

            val done = rowFor(completed)
            assertFalse("nothing to complete twice", done.canSwipeComplete)
            assertEquals(setOf(LessonStatus.CANCELLED), done.statusChoices)
            assertFalse(done.recedes)

            val off = rowFor(cancelled)
            assertFalse(off.canSwipeComplete)
            assertEquals(setOf(LessonStatus.SCHEDULED), off.statusChoices)
            assertTrue("a called-off lesson recedes — its hour was given back", off.recedes)

            val corrected = rowFor(mixed)
            assertEquals(LessonStatus.SCHEDULED, corrected.session.status)
            assertTrue(
                "a lesson still to come for somebody can be swiped done",
                corrected.canSwipeComplete,
            )
            assertFalse("and does not recede — it is not off", corrected.recedes)
        }

    @Test
    fun `a taught lesson with an absentee offers no swipe`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Математика")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        val boris = env.fixtures.pricedStudent("Борис", subject)
        val group = env.fixtures.group("11 «А»", subjectId = subject, members = setOf(anya, boris))

        val ids = env.fixtures.completedLesson(subject, listOf(anya, boris), at(12), groupId = group)
        val borisClass = env.classRepo.getSessionFor(ids.first())!!
            .classes.single { it.studentId == boris }
        env.classRepo.setAttendance(borisClass, Attended.ABSENT)

        val row = ScheduleRow(
            session = env.classRepo.getSessionFor(ids.first())!!,
            title = "",
            subjectName = "",
        )

        assertEquals(LessonStatus.COMPLETED, row.session.status)
        assertEquals("one of the two was in the room", 1, row.session.present)
        assertEquals(
            "a taught lesson can only be called off — an absence does not un-teach it",
            setOf(LessonStatus.CANCELLED),
            row.statusChoices,
        )
        assertFalse("so a lesson that reads as taught offers no swipe", row.canSwipeComplete)
    }

    /**
     * A group lesson's sheet lists the group's homework and a member's own whose next lesson it is — the
     * member named, since the sheet is titled by the group — and a tick given there stays, ticked.
     */
    @Test
    fun `a lesson's sheet lists its homework and ticks it done in place`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject()
        val anya = env.fixtures.pricedStudent("Аня", maths)
        val boris = env.fixtures.pricedStudent("Борис", maths)
        val group = env.fixtures.group(subjectId = maths, members = setOf(anya, boris))
        val lesson = env.fixtures.lesson(
            maths, listOf(anya, boris), Instant.parse("2026-06-03T12:00:00Z"), groupId = group,
        ).first()
        env.fixtures.homework(Assignee.Group(group), text = "группы")
        env.fixtures.homework(Assignee.Student(boris), text = "Бориса", at = env.clock.now().plus(60.seconds))
        val vm = vm(clock = env.clock)
        val session = env.classRepo.getSessionFor(lesson)!!

        val sheet = vm.homeworkAt(session).first()
        assertEquals(listOf("группы", "Бориса"), sheet.map { it.homework.text })
        assertEquals(listOf(null, "Борис"), sheet.map { it.who })

        vm.setHomeworkDone(sheet.first().homework, done = true)
        runCurrent()

        assertEquals(listOf(true, false), vm.homeworkAt(session).first().map { it.homework.isDone })
    }
}
