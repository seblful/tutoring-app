package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClassEditGroupTest : ClassEditTestFixture() {

    @Test
    fun `a group whose members are all archived cannot be booked`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        env.lessonChanges.archiveWithoutUndo(env.studentDao.getById(ann)!!.toDomain())
        env.lessonChanges.archiveWithoutUndo(env.studentDao.getById(bob)!!.toDomain())
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.ChooseTarget(ClassTarget.GROUP))
        vm.change(BookingIntent.ChooseGroup(groupId))
        vm.change(
            BookingIntent.Edit {
                it.copy(
                    date = env.clock.today().plus(3, DateTimeUnit.DAY),
                    timeFrom = LocalTime(10, 0),
                    durationMinutes = 60,
                )
            },
        )
        advanceUntilIdle()

        assertTrue(vm.state.current.groupHasNoMembers)
        assertFalse(vm.state.current.canSave)
    }

    @Test
    fun `a group lesson with every member unticked is refused out loud`() =
        runTest(main.dispatcher) {
            val (groupId, ann, bob) = seedGroup()
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            vm.change(BookingIntent.ChooseTarget(ClassTarget.GROUP))
            vm.change(BookingIntent.ChooseGroup(groupId))
            advanceUntilIdle()
            vm.change(BookingIntent.IncludeMember(ann, included = false))
            vm.change(BookingIntent.IncludeMember(bob, included = false))
            val date = env.clock.today().plus(3, DateTimeUnit.DAY)
            vm.change(
                BookingIntent.Edit {
                    it.copy(date = date, timeFrom = LocalTime(10, 0), durationMinutes = 60)
                },
            )
            advanceUntilIdle()

            var done = false
            vm.save { done = true }
            advanceUntilIdle()

            assertFalse(done)
            assertTrue(env.classes().isEmpty(), "a lesson billing nobody is not written")
            assertEquals(
                ClassEditViewModel.Event.SAVE_REFUSED_NO_MEMBERS,
                vm.events.stream.first(),
            )
        }

    @Test
    fun `a form opened from a group starts out booking that group - in its default mode`() =
        runTest(main.dispatcher) {
            val (groupId, _, _) = seedGroup()
            val vm = vm(groupId = groupId)
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(ClassTarget.GROUP, vm.state.current.form.target)
            assertEquals(groupId, vm.state.current.form.groupId)
            assertEquals(ClassMode.REMOTE, vm.state.current.form.mode)
        }

    @Test
    fun `a group can be booked for any subject - priced for its members or not`() = runTest(main.dispatcher) {
        val (groupId, _, _) = seedGroup()
        env.fixtures.subject(name = "Физика")

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf("Алгебра", "Физика"), vm.state.current.subjects.map { it.name })
    }

    @Test
    fun `choosing a group prefills the lesson's rate from the group`() = runTest(main.dispatcher) {
        val (groupId, _, _) = seedGroup(annPrice = Money.ofMajor(30), bobPrice = Money.ofMajor(25))
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        // The group's rate, not either member's individual price — and known from the group alone,
        // without waiting for a subject.
        assertEquals(groupRate, vm.state.current.form.price.amount)
        assertEquals(2, vm.state.current.members.size)
    }

    @Test
    fun `a rate already typed survives the group's prefill`() = runTest(main.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.ChooseTarget(ClassTarget.GROUP))
        vm.change(BookingIntent.Edit { it.copy(price = MoneyField("35")) })
        vm.change(BookingIntent.ChooseGroup(groupId))
        advanceUntilIdle()

        assertEquals(
            Money.ofMajor(35),
            vm.state.current.form.price.amount,
            "one lesson at an agreed rate isn't overruled by the group's",
        )
    }

    @Test
    fun `an archived member is left out of a new group lesson`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        env.lessonChanges.archiveWithoutUndo(env.studentRepo.getById(bob)!!)

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.state.current.members.map { it.id })

        val date = env.clock.today().plus(2, DateTimeUnit.DAY)
        vm.change(BookingIntent.Edit { it.copy(date = date, timeFrom = LocalTime(10, 0), durationMinutes = 60) })
        vm.save { }
        advanceUntilIdle()

        assertEquals(listOf(ann), env.classes().map { it.studentId }, "only the active member is billed")
    }

    @Test
    fun `a group lesson needs one valid rate`() {
        val base = ClassForm(
            target = ClassTarget.GROUP,
            groupId = 7, subjectId = 1,
            date = LocalDate(2026, 9, 1),
            timeFrom = LocalTime(10, 0),
            durationMinutes = 60,
        )
        assertFalse(base.isValid, "nothing said about the money")
        assertFalse(base.copy(price = MoneyField("abc")).isValid)
        assertTrue(base.copy(price = MoneyField("25,50")).isValid)
        assertTrue(base.copy(price = MoneyField("0")).isValid, "a free lesson is billed at 0")
    }

    @Test
    fun `saving a new group lesson writes one class per member at the group's rate`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        val date = env.clock.today().plus(2, DateTimeUnit.DAY)
        vm.change(BookingIntent.Edit { it.copy(date = date, timeFrom = LocalTime(10, 0), durationMinutes = 90) })
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        val saved = env.classes()
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.groupId == groupId }, "every class belongs to the group")
        assertTrue(saved.map { it.startMillis }.distinct().size == 1, "and to the same lesson")
        assertEquals(90, saved.first().durationMinutes)
        assertEquals(
            mapOf(ann to groupRate.minor, bob to groupRate.minor),
            saved.associate { it.studentId to it.priceMinor },
        )
    }

    @Test
    fun `a member excluded from this lesson gets no class at all`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        val date = env.clock.today().plus(2, DateTimeUnit.DAY)
        vm.change(BookingIntent.Edit { it.copy(date = date, timeFrom = LocalTime(10, 0), durationMinutes = 60) })
        // Bob is away this week. He stays on the roster; he just isn't at this lesson.
        vm.change(BookingIntent.IncludeMember(bob, false))
        advanceUntilIdle()

        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        val saved = env.classes()
        assertEquals(1, saved.size)
        assertEquals(ann, saved.single().studentId)
        assertTrue(saved.none { it.studentId == bob }, "no cancelled leftover")
        assertEquals(setOf(ann, bob), env.groupRepo.getMemberIds(groupId).toSet(), "the group keeps him")
    }

    @Test
    fun `excluding everyone books nothing`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        val date = env.clock.today().plus(2, DateTimeUnit.DAY)
        vm.change(BookingIntent.Edit { it.copy(date = date, timeFrom = LocalTime(10, 0), durationMinutes = 60) })
        vm.change(BookingIntent.IncludeMember(ann, false))
        vm.change(BookingIntent.IncludeMember(bob, false))
        advanceUntilIdle()

        vm.save { }
        advanceUntilIdle()

        assertTrue(env.classes().isEmpty())
    }

    @Test
    fun `a group that has a subject decides the lesson's subject`() = runTest(main.dispatcher) {
        val ann = env.fixtures.student(name = "Аня")
        val algebra = env.fixtures.subject(name = "Алгебра")
        val physics = env.fixtures.subject(name = "Физика")
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
        env.enrollmentRepo.enroll(ann, algebra, Money.ofMajor(30))
        val groupId = env.groupRepo.save(
            Group(name = "11 «А»", price = groupRate, subjectId = algebra),
            setOf(ann),
        )

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        // Only the group's subject is on offer, it is already chosen, and the picker is locked.
        assertEquals(listOf(algebra), vm.state.current.subjects.map { it.id })
        assertEquals(algebra, vm.state.current.form.subjectId)
        assertTrue(vm.state.current.subjectFixedByGroup)
        assertTrue(vm.state.current.subjects.none { it.id == physics }, "physics is not bookable for this group")
    }

    @Test
    fun `a group with no subject still offers every subject`() = runTest(main.dispatcher) {
        val (groupId, _, _) = seedGroup()
        env.fixtures.subject(name = "Физика")

        val vm = vm(groupId = groupId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(2, vm.state.current.subjects.size)
        assertFalse(vm.state.current.subjectFixedByGroup)
    }

    @Test
    fun `editing a group lesson loads every member and saves them all`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val start = Instant.parse("2026-09-01T09:00:00Z")
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.stored().subjects.first().id, start = start,
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(ClassTarget.GROUP, vm.state.current.form.target)
        assertEquals(groupRate, vm.state.current.form.price.amount, "the rate the lesson was booked at")
        assertEquals(listOf(ann, bob), vm.state.current.members.map { it.id })

        vm.change(BookingIntent.Edit { it.copy(price = MoneyField("28"), durationMinutes = 90) })
        vm.save { }
        advanceUntilIdle()

        val saved = env.classes()
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.durationMinutes == 90 })
        assertTrue(saved.all { it.priceMinor == Money.ofMajor(28).minor }, "one rate applies to the whole lesson")
    }

    @Test
    fun `editing an old lesson offers the current roster but ticks only its own members`() =
        runTest(main.dispatcher) {
            val (groupId, ann, bob) = seedGroup()
            val algebra = env.stored().subjects.first().id
            val ids = env.classRepo.book(
                groupId = groupId, subjectId = algebra, start = Instant.parse("2026-09-01T09:00:00Z"),
                durationMinutes = 60, mode = ClassMode.IN_PERSON,
                studentIds = listOf(ann, bob),
                price = groupRate,
            ).written()
            val carl = env.fixtures.student(name = "Вова")
            env.enrollmentRepo.enroll(carl, algebra, Money.ofMajor(20))
            env.groupRepo.save(Group(id = groupId, name = "11 «А»"), setOf(ann, carl))

            val vm = vm(id = ids.first())
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(listOf(ann, bob, carl), vm.state.current.members.map { it.id })
            assertEquals(setOf(carl), vm.state.current.form.excludedMemberIds)
            assertFalse(vm.state.current.isDirty, "loading a lesson is not the tutor's edit")

            vm.save { }
            advanceUntilIdle()
            assertEquals(2, env.classes().size)
            assertEquals(setOf(ann, bob), env.classes().map { it.studentId }.toSet())
        }

    @Test
    fun `unticking a member of a booked lesson removes their class on save`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.stored().subjects.first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.IncludeMember(bob, false))
        vm.save { }
        advanceUntilIdle()

        assertEquals(listOf(ann), env.classes().map { it.studentId })
    }

    @Test
    fun `ticking a group member who was not in a booked lesson adds their class`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.stored().subjects.first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()
        assertEquals(setOf(bob), vm.state.current.form.excludedMemberIds, "Bob is offered, unticked")

        vm.change(BookingIntent.IncludeMember(bob, true))
        vm.save { }
        advanceUntilIdle()

        val saved = env.classes()
        assertEquals(setOf(ann, bob), saved.map { it.studentId }.toSet())
        assertTrue(saved.all { it.groupId == groupId }, "the joiner is in the same lesson")
        assertEquals(1, saved.map { it.startMillis }.distinct().size)
    }

    @Test
    fun `emptying a booked lesson of every member is refused - not saved`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.stored().subjects.first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.IncludeMember(ann, false))
        vm.change(BookingIntent.IncludeMember(bob, false))
        advanceUntilIdle()

        assertTrue(vm.state.current.allMembersExcluded)
        assertFalse(vm.state.current.canSave, "nothing left to bill")
        assertEquals(2, env.classes().size, "both classes are still there")
    }

    @Test
    fun `unticking a billed member is counted - an unbilled one is not`() = runTest(main.dispatcher) {
        val (groupId, ann, bob) = seedGroup()
        val ids = env.classRepo.book(
            groupId = groupId, subjectId = env.stored().subjects.first().id,
            start = Instant.parse("2026-09-01T09:00:00Z"),
            durationMinutes = 60, mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann, bob),
            price = groupRate,
        ).written()
        val session = env.classRepo.getSessionFor(ids.first())!!
        env.classRepo.setStatus(session, LessonStatus.COMPLETED)
        val bobsClass = env.classRepo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        env.classRepo.setAttendance(bobsClass, Attended.ABSENT)

        val vm = vm(id = ids.first())
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(0, vm.state.current.billedDrops, "nothing unticked yet")
        assertEquals(SaveIntent.Save, vm.state.current.saveIntent, "so saving asks nothing")

        vm.change(BookingIntent.IncludeMember(bob, false))
        advanceUntilIdle()
        assertEquals(0, vm.state.current.billedDrops, "an absent member earned nothing to lose")
        assertEquals(SaveIntent.Save, vm.state.current.saveIntent)

        vm.change(BookingIntent.IncludeMember(ann, false))
        advanceUntilIdle()
        assertEquals(1, vm.state.current.billedDrops)
        assertEquals(SaveIntent.ConfirmBilledDrops(1), vm.state.current.saveIntent)
    }

    @Test
    fun `switching the target clears the other target's choices`() = runTest(main.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.ChooseTarget(ClassTarget.GROUP))
        advanceUntilIdle()
        assertEquals(groupId, vm.state.current.form.groupId) // the only group is auto-selected
        assertNull(vm.state.current.form.studentId)

        vm.change(BookingIntent.ChooseTarget(ClassTarget.STUDENT))
        advanceUntilIdle()
        assertNull(vm.state.current.form.groupId)
        assertTrue(vm.state.current.form.price.text.isEmpty(), "the group's rate goes with the group")
    }
}
