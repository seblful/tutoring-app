package com.seblful.klas.ui.schedule

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.seblful.klas.domain.CalendarLayout
import com.seblful.klas.testutil.observing
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
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

    private fun vm(
        clock: Clock = Clock.systemDefaultZone(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ) =
        ScheduleViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, mainRule.appScope,
            mainRule.dispatcher, clock, weekStart,
        )

    private fun startMillisOn(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    /** A fixed day at [hour], for tests that only need distinct lesson times. */
    private fun at(hour: Int): Instant =
        LocalDate.of(2026, 5, 18).atTime(hour, 0).atZone(zone).toInstant()

    @Test
    fun `add is disabled until at least one student and one subject exist`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.canAddClass)
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
        observing(vm.content)
        advanceUntilIdle()

        val rows = vm.content.value.rows
        assertEquals(2, rows.size)
        assertTrue("rows are sorted by start time", rows[0].klass.start < rows[1].klass.start)
        assertEquals("Аня", rows[0].title)
        assertEquals("Математика", rows[0].subjectName)
    }

    /**
     * The screen used to read `LocalDate.now()` in its own body, evaluated per composition and never
     * again on its own — so an app left open overnight went on marking yesterday as today.
     *
     * `delay` is virtual under the test scheduler, so crossing midnight costs no wall-clock time.
     *
     * Deliberately **no** `advanceUntilIdle()` anywhere here: a clock has no last emission, so there is
     * always another `delay` queued and "until idle" never arrives — the test would hang rather than
     * fail. Every step below advances by a stated amount.
     */
    @Test
    fun `today rolls over at midnight`() = runTest(mainRule.dispatcher) {
        val start = LocalDate.of(2026, 7, 15)
        // 22:00 on the 15th, so midnight is two hours out.
        val clock = MutableClock(start.atTime(22, 0).atZone(zone).toInstant(), zone)
        val twoHours = java.time.Duration.ofHours(2).toMillis()

        val vm = vm(clock)
        observing(vm.today)
        advanceTimeBy(1)
        assertEquals(start, vm.today.value)

        // One millisecond short of midnight. Both clocks move: the wall clock is what the date is read
        // from, the scheduler is what the sleep is waiting on.
        clock.advanceBy(twoHours - 2)
        advanceTimeBy(twoHours - 2)
        assertEquals("the moment before midnight is still today", start, vm.today.value)

        clock.advanceBy(2)
        advanceTimeBy(2)
        assertEquals("and then it is tomorrow", start.plusDays(1), vm.today.value)
    }

    /** A device that slept through more than one night wakes up on the right day, not the next one. */
    @Test
    fun `today catches up when the phone slept for days`() = runTest(mainRule.dispatcher) {
        val start = LocalDate.of(2026, 7, 15)
        val clock = MutableClock(start.atTime(22, 0).atZone(zone).toInstant(), zone)

        val vm = vm(clock)
        observing(vm.today)
        advanceTimeBy(1)
        assertEquals(start, vm.today.value)

        val threeDays = java.time.Duration.ofDays(3).toMillis()
        clock.advanceBy(threeDays)
        advanceTimeBy(threeDays)

        assertEquals(
            "the date is re-read from the clock, not counted up one sleep at a time",
            start.plusDays(3),
            vm.today.value,
        )
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
        observing(vm.weekDays)
        vm.selectDate(LocalDate.of(2026, 7, 15))
        advanceUntilIdle()

        val days = vm.weekDays.value
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 7, 13), days.first())
        assertEquals(LocalDate.of(2026, 7, 19), days.last())
    }

    /**
     * A phone set to start the week on Sunday moves **every** page, not just the strip: the week the agenda
     * opens on, the seven days in it, and the month grid's first cell. They are three readers of one
     * answer, which is what the calendar looked wrong in three different ways for when it was hardcoded.
     */
    @Test
    fun `a week that starts on Sunday anchors every calendar page on Sunday`() = runTest(mainRule.dispatcher) {
        val vm = vm(weekStart = DayOfWeek.SUNDAY)
        observing(vm.weekDays)
        observing(vm.monthGrid)
        vm.selectDate(LocalDate.of(2026, 7, 15)) // a Wednesday
        advanceUntilIdle()

        assertEquals("the Sunday before, not the Monday", LocalDate.of(2026, 7, 12), vm.visibleWeek.value)
        assertEquals(LocalDate.of(2026, 7, 12), vm.weekDays.value.first())
        assertEquals(LocalDate.of(2026, 7, 18), vm.weekDays.value.last())
        assertEquals(DayOfWeek.SUNDAY, vm.monthGrid.value.firstDate.dayOfWeek)
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
        observing(vm.content)
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
        observing(vm.content)
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
        // One class today, one on another day of the same week. Picked by excluding today rather
        // than as startOfWeek + 6: on the last day of the week that *is* today, and both lessons
        // would land on the selected day — failing for the calendar, not for the behaviour.
        val otherDay = CalendarLayout.week(CalendarLayout.startOfWeek(today)).first { it != today }
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
                startMillis = startMillisOn(otherDay, LocalTime.of(9, 0)),
                durationMinutes = 60,
                status = "SCHEDULED",
            ),
        )

        val vm = vm()
        observing(vm.content)
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
        observing(vm.dayLoads)
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
        observing(vm.monthGrid)
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
        observing(vm.dayLoads)
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
        observing(vm.dayLoads)
        vm.setViewMode(ScheduleViewMode.MONTH)
        vm.selectDate(LocalDate.of(2026, 7, 1))
        advanceUntilIdle()
        assertFalse(august in vm.dayLoads.value)

        vm.goToNextMonth()
        advanceUntilIdle()

        assertEquals(1, vm.dayLoads.value.getValue(august).total)
        assertEquals(
            mapOf(ClassStatus.COMPLETED to 1),
            vm.dayLoads.value.getValue(august).counts,
        )
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

        assertEquals("Борис", vm.timeConflict.value?.who)
        assertEquals(
            "the lesson stayed where it was",
            startMillisOn(day, LocalTime.of(9, 0)),
            env.classDao.items.value.single { it.id == moving }.startMillis,
        )

        vm.dismissTimeConflict()
        assertNull(vm.timeConflict.value)
    }

    /**
     * Cancelling gives the hour back on purpose, so re-booking it is legitimate — which makes
     * un-cancelling the way back into an hour somebody else now holds. The status path used to write
     * unconditionally, and the schedule simply drew both lessons at once.
     */
    @Test
    fun `a cancelled lesson cannot be returned to an hour taken since`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        val at = startMillisOn(day, LocalTime.of(10, 0))
        val cancelled = env.classDao.upsert(
            ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "CANCELLED"),
        )
        // The freed hour, re-booked for somebody else.
        env.classDao.upsert(
            ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED"),
        )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(cancelled)!!, ClassStatus.SCHEDULED)
        advanceUntilIdle()

        assertEquals("Борис", vm.timeConflict.value?.who)
        assertEquals(
            "the lesson stayed cancelled",
            "CANCELLED",
            env.classDao.items.value.single { it.id == cancelled }.status,
        )
    }

    @Test
    fun `a cancelled lesson returns to a free hour`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        val cancelled = env.classDao.upsert(
            ClassEntity(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(day, LocalTime.of(10, 0)),
                durationMinutes = 60, status = "CANCELLED",
            ),
        )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(cancelled)!!, ClassStatus.SCHEDULED)
        advanceUntilIdle()

        assertNull("nothing holds the hour, so nothing to refuse", vm.timeConflict.value)
        assertEquals("SCHEDULED", env.classDao.items.value.single().status)
    }

    // --- A write that fails is said out loud, not crashed on -------------------------------------

    /**
     * These actions are tapped in a sheet that closes as it is tapped, and the write is handed to the
     * application scope so it outlives the screen. What that scope does not give is a reply: a plain
     * `launch` reported a failure only to the scope's handler, so the tutor saw the sheet shut and the
     * lesson not change, with nothing to explain it. Before the handler existed it was worse — the
     * exception reached the thread's default handler and killed the process.
     */
    @Test
    fun `a status change that fails to commit is reported`() = runTest(mainRule.dispatcher) {
        val env = TestEnv()
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            ),
        )
        val vm = ScheduleViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, mainRule.appScope,
            mainRule.dispatcher, Clock.systemDefaultZone(),
        )
        val seen = mutableListOf<ScheduleViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }

        env.transactor.failAt = env.transactor.count + 1
        vm.setStatus(env.classRepo.getSessionFor(id)!!, ClassStatus.COMPLETED)
        advanceUntilIdle()

        assertEquals(listOf(ScheduleViewModel.Event.ACTION_FAILED), seen)
    }

    @Test
    fun `a delete that fails to commit is reported`() = runTest(mainRule.dispatcher) {
        val env = TestEnv()
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            ),
        )
        val vm = ScheduleViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, mainRule.appScope,
            mainRule.dispatcher, Clock.systemDefaultZone(),
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
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            ),
        )
        val vm = vm()
        val seen = mutableListOf<ScheduleViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }

        vm.delete(env.classRepo.getSessionFor(id)!!)
        advanceUntilIdle()

        assertTrue("nothing failed, so nothing to announce", seen.isEmpty())
    }

    /** Completing occupies no new time, so it must not be checked against the hour it already holds. */
    @Test
    fun `marking a lesson completed is never refused`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)),
                durationMinutes = 60, status = "SCHEDULED", priceMinor = 3000,
            ),
        )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(id)!!, ClassStatus.COMPLETED)
        advanceUntilIdle()

        assertNull(vm.timeConflict.value)
        assertEquals("COMPLETED", env.classDao.items.value.single().status)
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

        assertNull(vm.timeConflict.value)
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
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob)).id
        val day = LocalDate.of(2026, 7, 15)
        val at = startMillisOn(day, LocalTime.of(12, 0))
        val first = env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))
        env.classDao.upsert(ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))

        val vm = vm()
        val target = day.atTime(12, 30).atZone(zone).toInstant()
        vm.move(env.classRepo.getSessionFor(first)!!, target)
        advanceUntilIdle()

        assertNull(vm.timeConflict.value)
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
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob)).id
        val wednesday = LocalDate.of(2026, 7, 15)
        val at = startMillisOn(wednesday, LocalTime.of(12, 0))
        env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))
        env.classDao.upsert(ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))

        val vm = vm()
        observing(vm.content)
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(wednesday)
        advanceUntilIdle()

        val section = vm.content.value.sections.single()
        assertEquals(wednesday, section.date)
        assertEquals("two member classes, one agenda row", 1, section.rows.size)
        assertTrue(section.rows[0].isGroup)
        assertEquals("11 «А»", section.rows[0].title)
        assertEquals(listOf("Аня", "Борис"), section.rows[0].members)
    }

    @Test
    fun `a group lesson is one row, with its members inside it`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob)).id
        val today = LocalDate.now(zone)
        val at = startMillisOn(today, LocalTime.of(12, 0))
        env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))
        env.classDao.upsert(ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId))
        // Plus an unrelated individual class earlier the same day.
        env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED"))

        val vm = vm()
        observing(vm.content)
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
        // The row names who is in the room; the lesson itself is what the schedule acts on.
        assertEquals(listOf("Аня", "Борис"), group.members)
        assertEquals("both members' classes are the one lesson", 2, group.classes.size)
    }

    @Test
    fun `completing a group row completes every member's class`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob)).id
        val at = startMillisOn(LocalDate.now(zone), LocalTime.of(12, 0))
        env.classDao.upsert(ClassEntity(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED", groupId = groupId))
        env.classDao.upsert(ClassEntity(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, priceMinor = Money.ofMajor(20).minor, status = "SCHEDULED", groupId = groupId))

        val vm = vm()
        observing(vm.content)
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

            assertEquals(DeleteWarning(billed = false, group = false, classes = 1), warningFor(scheduled))
            assertEquals(DeleteWarning(billed = true, group = false, classes = 1), warningFor(completed))
            assertEquals(
                "a group lesson is several students' classes at once",
                DeleteWarning(billed = false, group = true, classes = 2),
                warningFor(groupScheduled),
            )
            assertEquals(
                DeleteWarning(billed = true, group = true, classes = 2),
                warningFor(groupCompleted),
            )
        }

    /**
     * `deleteWarning`'s three siblings: what the sheet's segments enable, whether the swipe gesture
     * is offered, and whether the card recedes. All were decided inside `ScheduleScreen`, so the rule
     * gating the product's highest-frequency gesture could be exercised only by swiping on a phone —
     * the group lesson whose absentee was cancelled in particular, which must still offer the swipe
     * (the members who are coming have not been taught yet) and must not recede.
     */
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
            // A planned group lesson whose absentee was corrected to cancelled — the lesson still
            // reads «Запланировано», because it is still to come for the member who is coming.
            val mixed = env.fixtures.lesson(subject, listOf(anya, boris), at(12), groupId = group)
            val borisClass = env.classRepo.getSessionFor(mixed.first())!!
                .classes.single { it.studentId == boris }
            env.classRepo.setStatus(com.seblful.klas.domain.ClassSession.of(borisClass), ClassStatus.CANCELLED)

            suspend fun rowFor(ids: List<Long>) =
                ScheduleRow(session = env.classRepo.getSessionFor(ids.first())!!, title = "", subjectName = "")

            val plain = rowFor(scheduled)
            assertTrue("a scheduled lesson can be swiped done", plain.canSwipeComplete)
            assertEquals(setOf(ClassStatus.COMPLETED, ClassStatus.CANCELLED), plain.statusChoices)
            assertFalse(plain.recedes)

            val done = rowFor(completed)
            assertFalse("nothing to complete twice", done.canSwipeComplete)
            assertEquals(setOf(ClassStatus.CANCELLED), done.statusChoices)
            assertFalse(done.recedes)

            val off = rowFor(cancelled)
            assertFalse(off.canSwipeComplete)
            assertEquals(setOf(ClassStatus.SCHEDULED), off.statusChoices)
            assertTrue("a called-off lesson recedes — its hour was given back", off.recedes)

            val corrected = rowFor(mixed)
            assertEquals(ClassStatus.SCHEDULED, corrected.session.status)
            assertTrue(
                "a lesson still to come for somebody can be swiped done",
                corrected.canSwipeComplete,
            )
            assertFalse("and does not recede — it is not off", corrected.recedes)
        }

    /**
     * The swipe and the badge cannot contradict each other, and nothing has to enforce that.
     *
     * Now that a lesson always has one of the three statuses, the worry is a card badged «Проведено»
     * that still invites completing — a taught group lesson whose absentee was cancelled afterwards has
     * a member who is not completed, and `statusChoices` is the union over members. It does not happen:
     * completing is legal only from *scheduled*, and a scheduled member would make the lesson read
     * «Запланировано». Pinned because the alternative was a redundant guard in `canSwipeComplete`.
     */
    @Test
    fun `a taught lesson with a cancelled member offers no swipe`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Математика")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        val boris = env.fixtures.pricedStudent("Борис", subject)
        val group = env.fixtures.group("11 «А»", subjectId = subject, members = setOf(anya, boris))

        val ids = env.fixtures.completedLesson(subject, listOf(anya, boris), at(12), groupId = group)
        val borisClass = env.classRepo.getSessionFor(ids.first())!!
            .classes.single { it.studentId == boris }
        env.classRepo.setStatus(com.seblful.klas.domain.ClassSession.of(borisClass), ClassStatus.CANCELLED)

        val row = ScheduleRow(
            session = env.classRepo.getSessionFor(ids.first())!!,
            title = "",
            subjectName = "",
        )

        assertEquals(ClassStatus.COMPLETED, row.session.status)
        assertEquals(
            "the absentee can be put back on the schedule, but not straight to completed",
            setOf(ClassStatus.SCHEDULED, ClassStatus.CANCELLED),
            row.statusChoices,
        )
        assertFalse("so a lesson that reads as taught offers no swipe", row.canSwipeComplete)
    }
}
