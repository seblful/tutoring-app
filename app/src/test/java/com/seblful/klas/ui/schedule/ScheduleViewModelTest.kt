package com.seblful.klas.ui.schedule

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
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
import com.seblful.klas.domain.DaySummary
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.valueOrNull
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

    /**
     * The day's content the screen would be drawing, insisted upon — `current`'s counterpart for a state
     * that is a *shape* as well as an arrival.
     *
     * A test that reads this while the schedule is showing a week now fails naming the reason, where before
     * it read an `emptyList()` default and asserted happily about a day the view model was not assembling.
     * That default is what let the same emptiness reach the screen: see [day].
     */
    private val StateFlow<Loadable<ScheduleContent>>.dayContent: ScheduleContent.Day
        get() = value.day().valueOrNull() ?: error(
            "The schedule is not showing a day: its content is ${value}. Either the view mode is WEEK, " +
                "or nothing has been collected yet — is `observing(…)` missing?"
        )

    /** [dayContent]'s counterpart for the week agenda. */
    private val StateFlow<Loadable<ScheduleContent>>.weekContent: ScheduleContent.Week
        get() = value.week().valueOrNull() ?: error(
            "The schedule is not showing a week: its content is ${value}. Either the view mode is not " +
                "WEEK, or nothing has been collected yet — is `observing(…)` missing?"
        )

    /**
     * The day is [Loadable.Loading] until its lessons have been read.
     *
     * The initial value used to be `ScheduleContent.Day(emptyList())` — *a day with no lessons* — which
     * is indistinguishable from a genuinely free day, so every cold start drew «Нет занятий» and a
     * book-a-lesson button over the tutor's actual morning until the query returned. This is the app's
     * opening screen, so it was also the first thing anybody saw.
     */
    @Test
    fun `the day is Loading until its lessons have been read`() = runTest(mainRule.dispatcher) {
        val vm = vm()

        assertEquals(Loadable.Loading, vm.content.value)

        observing(vm.content)
        advanceUntilIdle()

        // Read, and genuinely empty — which is the only state the empty prompt is honest in. A day with
        // no lessons summarises to nothing, which is what stops the summary line being drawn at all.
        assertEquals(
            Loadable.Ready(ScheduleContent.Day(emptyList(), DaySummary.NONE)),
            vm.content.value,
        )
    }

    /**
     * **One view's content is not an answer about another's** — the half of the loaded-yet problem that
     * `Loadable` alone could not see, and the one still visible on a device after it was introduced.
     *
     * Switching view re-keys the lesson query, and a `StateFlow` holds its previous value until the new one
     * arrives: the state was `Ready`, about the view the tutor had just left. `ScheduleContent` made that
     * readable as emptiness, because `rows`, `sections` and `summary` were defaulted on the interface — a
     * `Week` answered "what are this day's lessons" with `emptyList()`, and a `Day` answered "which days
     * does this week hold" the same way. So tapping «Неделя» drew «Нет занятий на этой неделе» over a full
     * week, and «День» drew «Нет занятий» with a book-a-lesson button, for as long as the new query took.
     *
     * Asserted on the narrowing rather than by racing the flow, because this is where the rule now lives
     * and it is a rule about *values*: no scheduler ordering can make a week's content into a day's.
     */
    @Test
    fun `content about one view never answers as another view's emptiness`() {
        val day: Loadable<ScheduleContent> =
            Loadable.Ready(ScheduleContent.Day(emptyList(), DaySummary.NONE))
        val week: Loadable<ScheduleContent> = Loadable.Ready(ScheduleContent.Week(emptyList()))

        // Each answers its own question — including when the answer is "nothing", which is the state the
        // empty prompt is honest in.
        assertEquals(Loadable.Ready(ScheduleContent.Day(emptyList(), DaySummary.NONE)), day.day())
        assertEquals(Loadable.Ready(ScheduleContent.Week(emptyList())), week.week())

        // And neither answers the other's. Not "empty": *not yet*, which draws the skeleton.
        assertEquals("a week's content says nothing about a day", Loadable.Loading, week.day())
        assertEquals("a day's content says nothing about a week", Loadable.Loading, day.week())
    }

    /** Loading narrows to loading, so a cold open is not mistaken for a shape mismatch either. */
    @Test
    fun `narrowing what has not arrived is still Loading`() {
        val nothing: Loadable<ScheduleContent> = Loadable.Loading

        assertEquals(Loadable.Loading, nothing.day())
        assertEquals(Loadable.Loading, nothing.week())
    }

    /** A fixed day at [hour], for tests that only need distinct lesson times. */
    private fun at(hour: Int): Instant =
        LocalDate.of(2026, 5, 18).atTime(hour, 0).atZone(zone).toInstant()

    /**
     * The day carries what it adds up to, so the header can print it without doing arithmetic in a
     * composable. The rules themselves are `DaySummaryTest`'s; what this pins is that the schedule
     * actually applies them to the day it is showing.
     */
    @Test
    fun `the selected day carries its own summary`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj, rate = Money.ofMajor(30))
        val today = LocalDate.now(zone)
        env.fixtures.lesson(
            subjectId = subj, students = listOf(sid),
            start = today.atTime(9, 0).atZone(zone).toInstant(),
            minutes = 90, rate = Money.ofMajor(30),
        )
        env.fixtures.cancelledLesson(
            subjectId = subj, students = listOf(sid),
            start = today.atTime(15, 0).atZone(zone).toInstant(),
            rate = Money.ofMajor(30),
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        val summary = vm.content.dayContent.summary
        assertEquals("the cancelled one is not a lesson the day holds", 1, summary.lessons)
        assertEquals(1, summary.cancelled)
        assertEquals(90, summary.minutes)
        // 30/h for 90 minutes, and nothing for the hour that was given back.
        assertEquals(Money.ofMajor(45), summary.money)
    }

    /**
     * The **"you are here"** mark moves with the clock.
     *
     * Both halves matter and only the second needs a schedule: [com.seblful.klas.domain.NowMarker] decides
     * *where* the mark goes and is tested on the JVM, but nothing else says the view model keeps asking.
     * A mark computed once is a mark that is wrong for the rest of the day, on a screen a tutor leaves
     * open all of it.
     *
     * As `today rolls over at midnight`: **no `advanceUntilIdle()`**. The minute tick has no last
     * emission, so "until idle" never arrives and the test would hang rather than fail.
     */
    @Test
    fun `the now mark follows the clock past a lesson`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj)
        val day = LocalDate.of(2026, 5, 18)
        // 08:59, one minute before the day's only lesson.
        val clock = MutableClock(day.atTime(8, 59).atZone(zone).toInstant(), zone)
        env.fixtures.lesson(
            subjectId = subj,
            students = listOf(sid),
            start = day.atTime(9, 0).atZone(zone).toInstant(),
        )

        val vm = vm(clock)
        observing(vm.content)
        observing(vm.nowMark)
        val minute = java.time.Duration.ofMinutes(1).toMillis()
        advanceTimeBy(minute)

        assertEquals("before the lesson, the mark is above it", 0, vm.nowMark.value)

        // Past nine now. Both clocks move: the wall clock is what the mark is read from, the scheduler
        // is what the tick is waiting on.
        clock.advanceBy(minute)
        advanceTimeBy(minute)

        assertEquals("once it has started, the mark is below it", 1, vm.nowMark.value)
    }

    /** A day that is not today has no "now" in it, and must not have one drawn at either end. */
    @Test
    fun `another day gets no now mark`() = runTest(mainRule.dispatcher) {
        val day = LocalDate.of(2026, 5, 18)
        val clock = MutableClock(day.atTime(12, 0).atZone(zone).toInstant(), zone)

        val vm = vm(clock)
        observing(vm.content)
        observing(vm.nowMark)
        advanceTimeBy(1)
        assertEquals(0, vm.nowMark.value)

        vm.selectDate(day.plusDays(1))
        advanceTimeBy(1)

        assertNull("tomorrow has no now", vm.nowMark.value)
    }

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
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(15, 0)), durationMinutes = 60, status = "SCHEDULED")
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED")
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = startMillisOn(today.plusDays(1), LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED")

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        val rows = vm.content.dayContent.rows
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
            env.fixtures.storedClass(
                    studentId = student, subjectId = subj, startMillis = startMillisOn(day, time),
                    durationMinutes = 60, status = "SCHEDULED",
                )
        }

        val vm = vm()
        observing(vm.content)
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(LocalDate.of(2026, 7, 15))
        advanceUntilIdle()

        val sections = vm.content.weekContent.sections
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
        env.fixtures.storedClass(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(nextWeek, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            )

        val vm = vm()
        observing(vm.content)
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(LocalDate.of(2026, 7, 15))
        advanceUntilIdle()
        assertTrue(vm.content.weekContent.sections.isEmpty())

        vm.goToNextWeek()
        advanceUntilIdle()

        assertEquals(listOf(nextWeek), vm.content.weekContent.sections.map { it.date })
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
        env.fixtures.storedClass(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            )
        env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = startMillisOn(otherDay, LocalTime.of(9, 0)),
                durationMinutes = 60,
                status = "SCHEDULED",
            )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        // The day view assembles the selected day — and the week's question has no answer at all, rather
        // than an empty one. That distinction is the fix for the flash on switching view: see [day].
        val day = vm.content.value.day().valueOrNull()
        assertEquals("the day view assembles the selected day", 1, day?.rows?.size)
        assertEquals(
            "and no week agenda exists to be drawn as an empty week",
            Loadable.Loading,
            vm.content.value.week(),
        )

        vm.setViewMode(ScheduleViewMode.WEEK)
        advanceUntilIdle()

        // Switching view swaps the query rather than adding a second live one.
        val week = vm.content.value.week().valueOrNull()
        assertEquals("both of the week's lessons", 2, week?.sections?.sumOf { it.rows.size })
        assertEquals(
            "and no day list exists to be drawn as an empty day",
            Loadable.Loading,
            vm.content.value.day(),
        )
    }

    @Test
    fun `the day view loads no markers at all`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        env.fixtures.storedClass(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            )

        val vm = vm()
        // A cell with no tally is this calendar saying «свободно», so the counts must not start at an
        // empty map — a fortnight off and a fortnight unread would draw the same grid. See [Loadable].
        assertEquals(Loadable.Loading, vm.dayLoads.value)

        observing(vm.dayLoads)
        vm.selectDate(day)
        advanceUntilIdle()
        // Still Loading, and that is the assertion: the day view queries no markers, and «no markers» is
        // not an answer it may leave behind. `Ready(emptyMap())` was — and it outlived the switch, so
        // «Месяц» re-appeared over a grid claiming a free month until its own query returned.
        assertEquals(
            "the day view draws no calendar, so it queries none — and claims none",
            Loadable.Loading,
            vm.dayLoads.value,
        )

        vm.setViewMode(ScheduleViewMode.WEEK)
        advanceUntilIdle()
        assertEquals(1, vm.dayLoads.current.getValue(day).total)
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
                env.fixtures.storedClass(
                        studentId = sid, subjectId = subj, startMillis = startMillisOn(day, time),
                        durationMinutes = 60, status = "SCHEDULED",
                    )
            }
        env.fixtures.storedClass(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(otherMonth, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "CANCELLED",
            )

        val vm = vm()
        observing(vm.dayLoads)
        vm.setViewMode(ScheduleViewMode.MONTH)
        vm.selectDate(LocalDate.of(2026, 7, 1))
        advanceUntilIdle()

        val loads = vm.dayLoads.current
        assertEquals(2, loads.getValue(inMonth).total)
        assertEquals("padding cells are marked too", 1, loads.getValue(padding).total)
        assertFalse("days outside the page are not loaded", otherMonth in loads)
    }

    @Test
    fun `paging to another month reloads its markers`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val august = LocalDate.of(2026, 8, 12)
        env.fixtures.storedClass(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(august, LocalTime.of(9, 0)),
                durationMinutes = 60, status = "COMPLETED",
            )

        val vm = vm()
        observing(vm.dayLoads)
        vm.setViewMode(ScheduleViewMode.MONTH)
        vm.selectDate(LocalDate.of(2026, 7, 1))
        advanceUntilIdle()
        assertFalse(august in vm.dayLoads.current)

        vm.goToNextMonth()
        advanceUntilIdle()

        assertEquals(1, vm.dayLoads.current.getValue(august).total)
        assertEquals(
            mapOf(LessonStatus.COMPLETED to 1),
            vm.dayLoads.current.getValue(august).counts,
        )
    }

    @Test
    fun `setStatus completes a class and captures its charge`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val id = env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 90, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED")

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.COMPLETED)
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
        val moving = env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED")
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(15, 0)), durationMinutes = 60, status = "SCHEDULED")

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
        val cancelled = env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "CANCELLED")
        // The freed hour, re-booked for somebody else.
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED")

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(cancelled)!!, LessonStatus.SCHEDULED)
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
        val cancelled = env.fixtures.storedClass(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(day, LocalTime.of(10, 0)),
                durationMinutes = 60, status = "CANCELLED",
            )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(cancelled)!!, LessonStatus.SCHEDULED)
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
        val id = env.fixtures.storedClass(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)),
                durationMinutes = 60, status = "SCHEDULED",
            )
        val vm = ScheduleViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, mainRule.appScope,
            mainRule.dispatcher, Clock.systemDefaultZone(),
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
                startMillis = startMillisOn(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)),
                durationMinutes = 60, status = "SCHEDULED",
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
        val id = env.fixtures.storedClass(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)),
                durationMinutes = 60, status = "SCHEDULED",
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
        val id = env.fixtures.storedClass(
                studentId = ann, subjectId = subj,
                startMillis = startMillisOn(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)),
                durationMinutes = 60, status = "SCHEDULED", priceMinor = 3000,
            )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.COMPLETED)
        advanceUntilIdle()

        assertNull(vm.timeConflict.value)
        assertEquals("COMPLETED", env.classDao.items.value.single().status)
    }

    @Test
    fun `a lesson can be nudged within its own hour`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        val id = env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED")

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
        val first = env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId)

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
        env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId)

        val vm = vm()
        observing(vm.content)
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(wednesday)
        advanceUntilIdle()

        val section = vm.content.weekContent.sections.single()
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
        env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = "SCHEDULED", groupId = groupId)
        // Plus an unrelated individual class earlier the same day.
        env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)), durationMinutes = 60, status = "SCHEDULED")

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        val rows = vm.content.dayContent.rows
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
        env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = "SCHEDULED", groupId = groupId)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, priceMinor = Money.ofMajor(20).minor, status = "SCHEDULED", groupId = groupId)

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStatus(vm.content.dayContent.rows.single().session, LessonStatus.COMPLETED)
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
        val id = env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "CANCELLED")

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
            // A planned group lesson one of whose members has already said they cannot come — the lesson
            // still reads «Запланировано», because it is still to come for the one who can.
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

    /**
     * The swipe and the badge cannot contradict each other, and nothing has to enforce it.
     *
     * The worry is a card badged «Проведено» that still invites completing. It used to be a real
     * possibility: the status was per member, so a taught lesson could hold a cancelled one, and
     * `statusChoices` was the *union* over the members — which offered «Проведено» for that member on a
     * card that already said the lesson was taught. Both halves of that are gone. The lesson has one
     * status, its choices are that status's own, and who was in the room is attendance — so an absentee
     * is a fact about them that cannot re-open the lesson.
     */
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

    // --- Narrowing the schedule to one student ---------------------------------------------------

    /**
     * Two students with a lesson each on the same day, and a group lesson holding one of them.
     * Returns the pair of student ids.
     */
    private suspend fun seedTwoStudentsOn(day: LocalDate): Pair<Long, Long> {
        val subj = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", subj)
        val bob = env.fixtures.pricedStudent("Борис", subj)
        env.fixtures.lesson(
            subjectId = subj, students = listOf(ann),
            start = day.atTime(9, 0).atZone(zone).toInstant(),
        )
        env.fixtures.lesson(
            subjectId = subj, students = listOf(bob),
            start = day.atTime(11, 0).atZone(zone).toInstant(),
        )
        return ann to bob
    }

    @Test
    fun `the schedule shows everyone until it is narrowed`() = runTest(mainRule.dispatcher) {
        val today = LocalDate.now(zone)
        seedTwoStudentsOn(today)

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        assertNull(vm.studentFilter.value)
        assertEquals(2, vm.content.dayContent.rows.size)
    }

    @Test
    fun `narrowing to a student leaves only their lessons, and names them`() =
        runTest(mainRule.dispatcher) {
            val today = LocalDate.now(zone)
            val (ann, _) = seedTwoStudentsOn(today)

            val vm = vm()
            observing(vm.content)
            observing(vm.studentFilter)
            advanceUntilIdle()

            vm.setStudentFilter(ann)
            advanceUntilIdle()

            assertEquals("Аня", vm.studentFilter.value?.name)
            assertEquals(1, vm.content.dayContent.rows.size)
            assertEquals("Аня", vm.content.dayContent.rows.single().title)
        }

    @Test
    fun `clearing the filter brings everyone back`() = runTest(mainRule.dispatcher) {
        val today = LocalDate.now(zone)
        val (ann, _) = seedTwoStudentsOn(today)

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStudentFilter(ann)
        advanceUntilIdle()

        vm.setStudentFilter(null)
        advanceUntilIdle()

        assertNull(vm.studentFilter.value)
        assertEquals(2, vm.content.dayContent.rows.size)
    }

    /**
     * **A filter narrows which lessons are shown, never what a lesson is.**
     *
     * Keeping only the filtered student's own class would leave a group lesson drawn as a session of one
     * — a group row with a roster of one — and, worse, the sheet's «Проведено» would then act on a
     * session the lesson does not consist of. See `GroupRules.lessonsWith`.
     */
    @Test
    fun `a group lesson the student is in stays a whole lesson`() = runTest(mainRule.dispatcher) {
        val today = LocalDate.now(zone)
        val subj = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", subj)
        val bob = env.fixtures.pricedStudent("Борис", subj)
        val cleo = env.fixtures.pricedStudent("Вера", subj)
        val group = env.fixtures.group(subjectId = subj, members = setOf(ann, bob, cleo))
        env.fixtures.lesson(
            subjectId = subj, students = listOf(ann, bob, cleo), groupId = group,
            start = today.atTime(9, 0).atZone(zone).toInstant(),
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStudentFilter(ann)
        advanceUntilIdle()

        val row = vm.content.dayContent.rows.single()
        assertTrue("it is still the group's lesson", row.isGroup)
        assertEquals("with all three of its classes", 3, row.classes.size)
        assertEquals(3, row.members.size)
    }

    @Test
    fun `a group lesson the student is not in is left out entirely`() = runTest(mainRule.dispatcher) {
        val today = LocalDate.now(zone)
        val subj = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", subj)
        val bob = env.fixtures.pricedStudent("Борис", subj)
        val group = env.fixtures.group(subjectId = subj, members = setOf(bob))
        env.fixtures.lesson(
            subjectId = subj, students = listOf(bob), groupId = group,
            start = today.atTime(9, 0).atZone(zone).toInstant(),
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStudentFilter(ann)
        advanceUntilIdle()

        assertTrue(vm.content.dayContent.rows.isEmpty())
    }

    /**
     * The calendar's tallies follow the filter too. A cell counting lessons the list under it does not
     * show would be the calendar contradicting the day it is a picker for.
     */
    @Test
    fun `the month's markers are narrowed with the list`() = runTest(mainRule.dispatcher) {
        val today = LocalDate.now(zone)
        val (ann, _) = seedTwoStudentsOn(today)

        val vm = vm()
        vm.setViewMode(ScheduleViewMode.MONTH)
        observing(vm.dayLoads)
        advanceUntilIdle()

        assertEquals(2, vm.dayLoads.current[today]?.counts?.get(LessonStatus.SCHEDULED))

        vm.setStudentFilter(ann)
        advanceUntilIdle()

        assertEquals(1, vm.dayLoads.current[today]?.counts?.get(LessonStatus.SCHEDULED))
    }

    /**
     * A filter naming somebody the database no longer holds resolves to *no filter at all*, rather than
     * to an empty schedule with no way out of it. Both the chip and the queries read this one value, so
     * they cannot disagree about whether narrowing is in effect.
     */
    @Test
    fun `a filter on a student who is not there shows everyone`() = runTest(mainRule.dispatcher) {
        val today = LocalDate.now(zone)
        seedTwoStudentsOn(today)

        val vm = vm()
        observing(vm.content)
        observing(vm.studentFilter)
        advanceUntilIdle()

        vm.setStudentFilter(9999L)
        advanceUntilIdle()

        assertNull(vm.studentFilter.value)
        assertEquals(2, vm.content.dayContent.rows.size)
    }

    @Test
    fun `an archived student the schedule is filtered by keeps their name`() =
        runTest(mainRule.dispatcher) {
            // The menu only offers active students, but one already being looked at must not lose the
            // chip that says so — the chip is the only way back to everyone else.
            val today = LocalDate.now(zone)
            val (ann, _) = seedTwoStudentsOn(today)

            val vm = vm()
            observing(vm.content)
            observing(vm.studentFilter)
            observing(vm.filterableStudents)
            advanceUntilIdle()
            vm.setStudentFilter(ann)
            advanceUntilIdle()

            env.studentRepo.setArchived(env.studentRepo.getById(ann)!!, archived = true)
            advanceUntilIdle()

            assertEquals("Аня", vm.studentFilter.value?.name)
            assertFalse(vm.filterableStudents.value.any { it.id == ann })
        }
}
