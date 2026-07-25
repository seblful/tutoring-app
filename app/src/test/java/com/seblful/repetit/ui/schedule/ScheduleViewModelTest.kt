package com.seblful.repetit.ui.schedule

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.testutil.MainDispatcherRule
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.seblful.repetit.domain.CalendarLayout
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun vm() = ScheduleViewModel(env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo)

    private fun startMillisOn(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `add is disabled until at least one student and one subject exist`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.canAddClass.collect {} }
        advanceUntilIdle()
        assertFalse(vm.canAddClass.value)

        env.studentDao.upsert(StudentEntity(name = "Аня"))
        advanceUntilIdle()
        assertFalse("a student without any subject still can't book", vm.canAddClass.value)

        env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        advanceUntilIdle()
        assertTrue(vm.canAddClass.value)
    }

    @Test
    fun `rows show only the selected day's classes, time-sorted, with student and subject names`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val today = LocalDate.now(zone)
        // Two classes today (out of order) and one tomorrow.
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(15, 0)), durationMinutes = 60, status = "SCHEDULED"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = startMillisOn(today.plusDays(1), LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED"))

        val vm = vm()
        backgroundScope.launch { vm.content.collect {} }
        advanceUntilIdle()

        val rows = vm.content.value.rows
        assertEquals(2, rows.size)
        assertTrue("rows are sorted by start time", rows[0].klass.start < rows[1].klass.start)
        assertEquals("Аня", rows[0].title)
        assertEquals("Математика", rows[0].subjectName)
    }

    @Test
    fun `day navigation moves the selected date and today resets it`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val today = LocalDate.now(zone)

        vm.goToNextDay()
        assertEquals(today.plusDays(1), vm.selectedDate.value)
        vm.goToPreviousDay()
        vm.goToPreviousDay()
        assertEquals(today.minusDays(1), vm.selectedDate.value)
        vm.goToToday()
        assertEquals(today, vm.selectedDate.value)
    }

    @Test
    fun `selecting a day repoints the list and brings the calendar page with it`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val target = LocalDate.of(2026, 11, 20)

        vm.selectDate(target)

        assertEquals(target, vm.selectedDate.value)
        assertEquals(YearMonth.of(2026, 11), vm.visibleMonth.value)
    }

    @Test
    fun `day navigation carries the calendar page across a month boundary`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 11, 30))

        vm.goToNextDay()

        assertEquals(LocalDate.of(2026, 12, 1), vm.selectedDate.value)
        assertEquals(YearMonth.of(2026, 12), vm.visibleMonth.value)
    }

    @Test
    fun `paging months browses without moving the selected day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 11, 20))

        vm.goToNextMonth()
        assertEquals(YearMonth.of(2026, 12), vm.visibleMonth.value)
        vm.goToPreviousMonth()
        vm.goToPreviousMonth()
        assertEquals(YearMonth.of(2026, 10), vm.visibleMonth.value)
        assertEquals("browsing must not repoint the day list", LocalDate.of(2026, 11, 20), vm.selectedDate.value)
    }

    @Test
    fun `opening a calendar view snaps its page back to the selected day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 11, 20))
        // Browse both pages away, then reopen each view.
        vm.goToNextMonth()
        vm.goToNextWeek()

        vm.setViewMode(ScheduleViewMode.MONTH)
        assertEquals(ScheduleViewMode.MONTH, vm.viewMode.value)
        assertEquals(YearMonth.of(2026, 11), vm.visibleMonth.value)

        vm.goToNextWeek()
        vm.setViewMode(ScheduleViewMode.WEEK)
        assertEquals(ScheduleViewMode.WEEK, vm.viewMode.value)
        assertEquals("the week containing 20 Nov 2026", LocalDate.of(2026, 11, 16), vm.visibleWeek.value)
    }

    @Test
    fun `paging weeks browses without moving the selected day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 7, 15)) // a Wednesday
        assertEquals(LocalDate.of(2026, 7, 13), vm.visibleWeek.value)

        vm.goToNextWeek()
        assertEquals(LocalDate.of(2026, 7, 20), vm.visibleWeek.value)
        vm.goToPreviousWeek()
        vm.goToPreviousWeek()
        assertEquals(LocalDate.of(2026, 7, 6), vm.visibleWeek.value)
        assertEquals("browsing must not repoint the selection", LocalDate.of(2026, 7, 15), vm.selectedDate.value)
    }

    @Test
    fun `the week strip shows seven contiguous days from the visible Monday`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.weekDays.collect {} }
        vm.selectDate(LocalDate.of(2026, 7, 15))
        advanceUntilIdle()

        val days = vm.weekDays.value
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 7, 13), days.first())
        assertEquals(LocalDate.of(2026, 7, 19), days.last())
    }

    @Test
    fun `week sections group the whole week by day and skip empty days`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val other = env.studentDao.upsert(StudentEntity(name = "Пётр"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val monday = LocalDate.of(2026, 7, 13)
        val thursday = LocalDate.of(2026, 7, 16)
        val nextWeek = LocalDate.of(2026, 7, 20)
        // Monday out of time order, so section rows must come back sorted.
        listOf(
            Triple(monday, LocalTime.of(15, 0), sid),
            Triple(monday, LocalTime.of(9, 0), other),
            Triple(thursday, LocalTime.of(11, 0), sid),
            Triple(nextWeek, LocalTime.of(9, 0), sid),
        ).forEach { (day, time, student) ->
            env.classDao.upsert(
                ClassEntity(
                    studentId = student, subjectId = subj, startMillis = startMillisOn(day, time),
                    durationMinutes = 60, status = "SCHEDULED",
                )
            )
        }

        val vm = vm()
        backgroundScope.launch { vm.content.collect {} }
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(LocalDate.of(2026, 7, 15))
        advanceUntilIdle()

        val sections = vm.content.value.sections
        assertEquals("only days with classes get a section", 2, sections.size)
        assertEquals(monday, sections[0].date)
        assertEquals(thursday, sections[1].date)
        assertEquals(2, sections[0].rows.size)
        assertTrue("rows within a day are time-sorted", sections[0].rows[0].klass.start < sections[0].rows[1].klass.start)
        assertEquals("Пётр", sections[0].rows[0].title)
        assertEquals("Математика", sections[0].rows[0].subjectName)
    }

    @Test
    fun `week sections follow the visible week, not the selected day`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val nextWeek = LocalDate.of(2026, 7, 20)
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(nextWeek, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            )
        )

        val vm = vm()
        backgroundScope.launch { vm.content.collect {} }
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(LocalDate.of(2026, 7, 15))
        advanceUntilIdle()
        assertTrue(vm.content.value.sections.isEmpty())

        vm.goToNextWeek()
        advanceUntilIdle()

        assertEquals(listOf(nextWeek), vm.content.value.sections.map { it.date })
    }

    @Test
    fun `only the active view's lessons are assembled`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val today = LocalDate.now(zone)
        // One class today, one later this week.
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            ),
        )
        env.classDao.upsert(
            ClassEntity(
                studentId = sid,
                subjectId = subj,
                startMillis = startMillisOn(CalendarLayout.startOfWeek(today).plusDays(6), LocalTime.of(9, 0)),
                durationMinutes = 60,
                status = "SCHEDULED",
            ),
        )

        val vm = vm()
        backgroundScope.launch { vm.content.collect {} }
        advanceUntilIdle()

        // The day view assembles the selected day — and no week agenda exists to go stale.
        val day = vm.content.value
        assertTrue("the day view shows a day", day is ScheduleContent.Day)
        assertEquals(1, day.rows.size)
        assertTrue("no week agenda is assembled for the day view", day.sections.isEmpty())

        vm.setViewMode(ScheduleViewMode.WEEK)
        advanceUntilIdle()

        // Switching view swaps the query rather than adding a second live one.
        val week = vm.content.value
        assertTrue("the week view shows a week", week is ScheduleContent.Week)
        assertTrue("no day list is assembled for the week view", week.rows.isEmpty())
        assertEquals("both of the week's lessons", 2, week.sections.sumOf { it.rows.size })
    }

    @Test
    fun `the day view loads no markers at all`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            )
        )

        val vm = vm()
        backgroundScope.launch { vm.dayLoads.collect {} }
        vm.selectDate(day)
        advanceUntilIdle()
        assertTrue("the day view draws no calendar, so it queries none", vm.dayLoads.value.isEmpty())

        vm.setViewMode(ScheduleViewMode.WEEK)
        advanceUntilIdle()
        assertEquals(1, vm.dayLoads.value.getValue(day).total)
    }

    @Test
    fun `the calendar grid follows the visible month`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.monthGrid.collect {} }
        vm.selectDate(LocalDate.of(2026, 7, 15))
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 7), vm.monthGrid.value.month)
        assertTrue(LocalDate.of(2026, 7, 15) in vm.monthGrid.value.weeks.flatten())
    }

    @Test
    fun `day markers cover the whole page, padding days included`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        // July 2026 starts on a Wednesday, so 30 June is a padding cell on the first row.
        val padding = LocalDate.of(2026, 6, 30)
        val inMonth = LocalDate.of(2026, 7, 15)
        val otherMonth = LocalDate.of(2026, 9, 1)
        listOf(padding to LocalTime.of(9, 0), inMonth to LocalTime.of(9, 0), inMonth to LocalTime.of(15, 0))
            .forEach { (day, time) ->
                env.classDao.upsert(
                    ClassEntity(
                        studentId = sid, subjectId = subj, startMillis = startMillisOn(day, time),
                        durationMinutes = 60, status = "SCHEDULED",
                    )
                )
            }
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(otherMonth, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "CANCELLED",
            )
        )

        val vm = vm()
        backgroundScope.launch { vm.dayLoads.collect {} }
        vm.setViewMode(ScheduleViewMode.MONTH)
        vm.selectDate(LocalDate.of(2026, 7, 1))
        advanceUntilIdle()

        val loads = vm.dayLoads.value
        assertEquals(2, loads.getValue(inMonth).total)
        assertEquals("padding cells are marked too", 1, loads.getValue(padding).total)
        assertFalse("days outside the page are not loaded", otherMonth in loads)
    }

    @Test
    fun `paging to another month reloads its markers`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val august = LocalDate.of(2026, 8, 12)
        env.classDao.upsert(
            ClassEntity(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(august, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "COMPLETED",
            )
        )

        val vm = vm()
        backgroundScope.launch { vm.dayLoads.collect {} }
        vm.setViewMode(ScheduleViewMode.MONTH)
        vm.selectDate(LocalDate.of(2026, 7, 1))
        advanceUntilIdle()
        assertFalse(august in vm.dayLoads.value)

        vm.goToNextMonth()
        advanceUntilIdle()

        assertEquals(1, vm.dayLoads.value.getValue(august).total)
        assertEquals(setOf(ClassStatus.COMPLETED), vm.dayLoads.value.getValue(august).statuses)
    }

    @Test
    fun `setStatus completes a class and captures its charge`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 90, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED"))

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(id)!!, ClassStatus.COMPLETED)
        advanceUntilIdle()

        val saved = env.classDao.items.value.single { it.id == id }
        assertEquals("COMPLETED", saved.status)
        assertEquals(Money.ofMajor(45).minor, saved.chargeMinor) // 30/h * 90 min
    }

    // --- Move: the destination hour has to be free ---

    @Test
    fun `a lesson cannot be moved onto an hour another lesson already holds`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        val moving = env.classDao.upsert(
            ClassEntity(studentId = ann, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED"),
        )
        env.classDao.upsert(
            ClassEntity(studentId = bob, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(15, 0)), durationMinutes = 60, status = "SCHEDULED"),
        )

        val vm = vm()
        val target = day.atTime(15, 30).atZone(zone).toInstant()
        vm.move(env.classRepo.getSessionFor(moving)!!, target)
        advanceUntilIdle()

        assertEquals("Борис", vm.moveConflict.value?.who)
        assertEquals(
            "the lesson stayed where it was",
            startMillisOn(day, LocalTime.of(9, 0)),
            env.classDao.items.value.single { it.id == moving }.startMillis,
        )

        vm.dismissMoveConflict()
        assertNull(vm.moveConflict.value)
    }

    @Test
    fun `a lesson can be nudged within its own hour`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        val id = env.classDao.upsert(
            ClassEntity(studentId = ann, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED"),
        )

        val vm = vm()
        // Ten minutes later overlaps where it currently is — it must not block itself.
        vm.move(env.classRepo.getSessionFor(id)!!, day.atTime(9, 10).atZone(zone).toInstant())
        advanceUntilIdle()

        assertNull(vm.moveConflict.value)
        assertEquals(
            startMillisOn(day, LocalTime.of(9, 10)),
            env.classDao.items.value.single().startMillis,
        )
    }

    @Test
    fun `a group lesson moves as one, without clashing with its own members`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
        val day = LocalDate.of(2026, 7, 15)
        val at = startMillisOn(day, LocalTime.of(12, 0))
        val first = env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))
        env.classDao.upsert(ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))

        val vm = vm()
        val target = day.atTime(12, 30).atZone(zone).toInstant()
        vm.move(env.classRepo.getSessionFor(first)!!, target)
        advanceUntilIdle()

        assertNull(vm.moveConflict.value)
        assertEquals(
            "both members moved together",
            listOf(target.toEpochMilli(), target.toEpochMilli()),
            env.classDao.items.value.map { it.startMillis },
        )
    }

    @Test
    fun `the week agenda collapses a group lesson exactly like the day list`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
        val wednesday = LocalDate.of(2026, 7, 15)
        val at = startMillisOn(wednesday, LocalTime.of(12, 0))
        env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))
        env.classDao.upsert(ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))

        val vm = vm()
        backgroundScope.launch { vm.content.collect {} }
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(wednesday)
        advanceUntilIdle()

        val section = vm.content.value.sections.single()
        assertEquals(wednesday, section.date)
        assertEquals("two member classes, one agenda row", 1, section.rows.size)
        assertTrue(section.rows[0].isGroup)
        assertEquals("11 «А»", section.rows[0].title)
        assertEquals(listOf("Аня", "Борис"), section.rows[0].members.map { it.title })
    }

    @Test
    fun `a group lesson is one row, with its members inside it`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
        val today = LocalDate.now(zone)
        val at = startMillisOn(today, LocalTime.of(12, 0))
        env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))
        env.classDao.upsert(ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))
        // Plus an unrelated individual class earlier the same day.
        env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED"))

        val vm = vm()
        backgroundScope.launch { vm.content.collect {} }
        advanceUntilIdle()

        val rows = vm.content.value.rows
        assertEquals("the group's two classes collapse into one row", 2, rows.size)
        val individual = rows.first()
        val group = rows.last()
        assertFalse(individual.isGroup)
        assertEquals("Аня", individual.title)
        assertTrue(group.isGroup)
        assertEquals("11 «А»", group.title)
        assertEquals("Алгебра", group.subjectName)
        assertEquals(listOf("Аня", "Борис"), group.members.map { it.title })
        // Each member row acts on that member's class alone — that's how one absentee is corrected.
        assertTrue(group.members.none { it.isGroup })
        assertEquals(1, group.members.first().classes.size)
    }

    @Test
    fun `completing a group row completes every member's class`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
        val at = startMillisOn(LocalDate.now(zone), LocalTime.of(12, 0))
        env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED", groupId = groupId))
        env.classDao.upsert(ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, priceMinor = Money.ofMajor(20).minor, status = "SCHEDULED", groupId = groupId))

        val vm = vm()
        backgroundScope.launch { vm.content.collect {} }
        advanceUntilIdle()
        vm.setStatus(vm.content.value.rows.single().session, ClassStatus.COMPLETED)
        advanceUntilIdle()

        val saved = env.classDao.items.value
        assertTrue(saved.all { it.status == "COMPLETED" })
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
        val id = env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "CANCELLED"))

        val vm = vm()
        vm.delete(env.classRepo.getSessionFor(id)!!)
        advanceUntilIdle()

        assertTrue(env.classDao.items.value.isEmpty())
    }
}
