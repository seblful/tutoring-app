package com.seblful.klas.ui.schedule

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.seblful.klas.domain.CalendarLayout
import com.seblful.klas.domain.DaySummary
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.valueOrNull
import com.seblful.klas.ui.navigation.Arrival
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
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.homeworkRepo, mainRule.appScope,
            mainRule.dispatcher, clock, weekStart,
        )

    private fun startMillisOn(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    /**
     * The day's content the screen would be drawing, insisted upon — `current`'s counterpart for a day
     * read out of the week the view model assembles (see [day]).
     *
     * A test that reads this before the week holding the selected day has arrived fails naming the
     * reason, where an empty default would have let it assert happily about a day nobody had read.
     */
    private val ScheduleViewModel.dayContent: ScheduleContent.Day
        get() = content.value.day(selectedDate.value).valueOrNull() ?: error(
            "The selected day ${selectedDate.value} has not been read: the content is ${content.value}. " +
                "Is `observing(vm.content)` missing, or does the scheduler need advancing?"
        )

    /** [dayContent]'s counterpart for the week agenda. */
    private val StateFlow<Loadable<ScheduleContent.Week>>.weekContent: ScheduleContent.Week
        get() = value.valueOrNull() ?: error(
            "The week has not been read: its content is ${value}. Is `observing(…)` missing?"
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
        assertEquals(Loadable.Loading, vm.content.value.day(vm.selectedDate.value))

        observing(vm.content)
        advanceUntilIdle()

        // Read, and genuinely empty — which is the only state the empty prompt is honest in. A day with
        // no lessons summarises to nothing, which is what stops the summary line being drawn at all.
        assertEquals(
            Loadable.Ready(ScheduleContent.Day(vm.selectedDate.value, emptyList(), DaySummary.NONE)),
            vm.content.value.day(vm.selectedDate.value),
        )
    }

    /**
     * **A step into the next week does not blink the day through Loading** — the day on screen stays up,
     * named by its own date, until the next one is read.
     *
     * Every change of day used to emit Loading first, so the list went to a skeleton and back on each tap,
     * and the summary line under the day's name vanished with it and jumped the list. Within a week a day
     * is now a lookup; across a week boundary the previous day is what the screen keeps drawing, which is
     * only honest because it says which day it is.
     */
    @Test
    fun `stepping into the next week goes straight from one day to the next`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student()
        val subj = env.fixtures.subject()
        val sunday = LocalDate.of(2026, 7, 19)
        val monday = sunday.plusDays(1)
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = startMillisOn(monday, LocalTime.of(9, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
        )
        val vm = vm()
        vm.selectDate(sunday)
        val seen = mutableListOf<Loadable<ScheduleContent.Day>>()
        backgroundScope.launch { vm.shownDay.collect { seen += it } }
        advanceUntilIdle()
        seen.clear()

        vm.goToNextDay()
        advanceUntilIdle()

        assertFalse("no Loading between two days that answer at once: $seen", Loadable.Loading in seen)
        val shown = vm.shownDay.current
        assertEquals("the day names the date it is", monday, shown.date)
        assertEquals(1, shown.rows.size)
    }

    /**
     * A week answers for a day it holds — the day's section, or a free day — and for no other day.
     *
     * Without it «Неделя» → «День» drew an empty list for the frames the day took to re-read, though the
     * week in hand already held every lesson on it.
     */
    @Test
    fun `a week answers for the days it holds and no others`() {
        val monday = LocalDate.of(2026, 7, 13)
        val tuesday = monday.plusDays(1)
        val summary = DaySummary.of(emptyList())
        val week: Loadable<ScheduleContent.Week> = Loadable.Ready(
            ScheduleContent.Week(monday, listOf(WeekSection(tuesday, emptyList(), summary))),
        )

        assertEquals(
            Loadable.Ready(ScheduleContent.Day(tuesday, emptyList(), summary)),
            week.day(tuesday),
        )
        assertEquals(
            "a day in the week with no section is free",
            Loadable.Ready(ScheduleContent.Day(monday.plusDays(6), emptyList(), DaySummary.NONE)),
            week.day(monday.plusDays(6)),
        )
        assertEquals("the next week is not this week's to answer", Loadable.Loading, week.day(monday.plusDays(7)))
        assertEquals(Loadable.Loading, week.day(monday.minusDays(1)))
    }

    /** A week that has not arrived answers for no day, so a cold open is not mistaken for a free one. */
    @Test
    fun `a week that has not arrived answers for no day`() {
        val nothing: Loadable<ScheduleContent.Week> = Loadable.Loading

        assertEquals(Loadable.Loading, nothing.day(LocalDate.of(2026, 7, 15)))
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

        val summary = vm.dayContent.summary
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

    /**
     * «Идёт» arrives and leaves on its own — which is the whole claim of not storing it.
     *
     * The badge is drawn from this set, so what the schedule says about a lesson in progress is decided
     * here, once a minute, against the injected clock. A test that only checked the middle of the lesson
     * would miss the half that matters: nothing writes an end, so the word has to *stop* by itself too.
     *
     * As `the now mark follows the clock past a lesson`: **no `advanceUntilIdle()`**, the tick never ends.
     */
    @Test
    fun `a lesson is marked as running only while it is running`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj)
        val day = LocalDate.of(2026, 5, 18)
        // 08:59, one minute before an hour-long lesson at nine.
        val clock = MutableClock(day.atTime(8, 59).atZone(zone).toInstant(), zone)
        env.fixtures.lesson(
            subjectId = subj,
            students = listOf(sid),
            start = day.atTime(9, 0).atZone(zone).toInstant(),
            minutes = 60,
        )

        val vm = vm(clock)
        observing(vm.content)
        observing(vm.running)
        val minute = java.time.Duration.ofMinutes(1).toMillis()
        advanceTimeBy(minute)
        assertEquals("not yet", emptySet<Long>(), vm.running.value)

        val lessonId = vm.dayContent.rows.single().session.lessonId
        clock.advanceBy(minute)
        advanceTimeBy(minute)
        assertEquals("on now", setOf(lessonId), vm.running.value)

        // Through to the far side of the hour: nothing wrote an end, so the word has to stop by itself.
        clock.advanceBy(60 * minute)
        advanceTimeBy(60 * minute)
        assertEquals("over, and unmarked rather than running", emptySet<Long>(), vm.running.value)
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
        assertEquals("nothing is claimed before the setup has been read", Loadable.Loading, vm.canAddClass.value)
        observing(vm.canAddClass)
        advanceUntilIdle()
        assertEquals(false, vm.canAddClass.value.valueOrNull())

        env.studentDao.upsert(StudentEntity(name = "Аня"))
        advanceUntilIdle()
        assertEquals("a student without any subject still can't book", false, vm.canAddClass.value.valueOrNull())

        env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        advanceUntilIdle()
        assertEquals(true, vm.canAddClass.value.valueOrNull())
    }

    @Test
    fun `rows show only the selected day's classes, time-sorted, with student and subject names`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val today = LocalDate.now(zone)
        // Two classes today (out of order) and one tomorrow.
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(15, 0)), durationMinutes = 60, status = LessonStatus.SCHEDULED)
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)), durationMinutes = 60, status = LessonStatus.SCHEDULED)
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = startMillisOn(today.plusDays(1), LocalTime.of(9, 0)), durationMinutes = 60, status = LessonStatus.SCHEDULED)

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        val rows = vm.dayContent.rows
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

    /**
     * Left on today overnight, the schedule is on today in the morning — the Day view opened on yesterday,
     * with no «now» line, because the selection was set once and only `today` rolled over. A day the tutor
     * browsed to is theirs and stays where they put it.
     */
    @Test
    fun `a selection left on today follows it past midnight, a browsed one stays`() = runTest(mainRule.dispatcher) {
        val start = LocalDate.of(2026, 7, 15)
        val clock = MutableClock(start.atTime(22, 0).atZone(zone).toInstant(), zone)
        val tenHours = java.time.Duration.ofHours(10).toMillis()

        val vm = vm(clock)
        observing(vm.today)
        advanceTimeBy(1)

        clock.advanceBy(tenHours)
        advanceTimeBy(tenHours)
        assertEquals("on today, it moves with today", start.plusDays(1), vm.selectedDate.value)

        val browsed = start.plusDays(5)
        vm.selectDate(browsed)
        val day = java.time.Duration.ofDays(1).toMillis()
        clock.advanceBy(day)
        advanceTimeBy(day)
        assertEquals("a day the tutor picked is not moved", browsed, vm.selectedDate.value)
    }

    /**
     * A tap in the tray is an arrival, and one arrival moves the schedule once. The screen hands it over on
     * every return — it leaves composition whenever a lesson or another tab covers it — and honouring it
     * each time sent the tutor back to the notification's day after every edit, for the rest of the process.
     */
    @Test
    fun `an arrival is honoured once, and a new one again`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val today = LocalDate.now(zone)
        val tapped = Arrival<ScheduleTarget>(ScheduleTarget.Today)

        vm.selectDate(today.plusDays(3))
        vm.arrive(tapped)
        assertEquals(today, vm.selectedDate.value)

        vm.selectDate(today.plusDays(3))
        vm.arrive(tapped)
        assertEquals("the same tap, handed over again on return", today.plusDays(3), vm.selectedDate.value)

        vm.arrive(Arrival(ScheduleTarget.Today))
        assertEquals("a second tap on the notification", today, vm.selectedDate.value)

        vm.selectDate(today.plusDays(3))
        vm.arrive(null)
        assertEquals("no arrival is nobody asking", today.plusDays(3), vm.selectedDate.value)
    }

    /** A digest tapped after midnight is still about its own day, not the one it is tapped on. */
    @Test
    fun `a day's arrival opens that day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val yesterday = LocalDate.now(zone).minusDays(1)

        vm.arrive(Arrival(ScheduleTarget.Day(yesterday)))

        assertEquals(yesterday, vm.selectedDate.value)
    }

    /**
     * A reminder's tap opens its lesson the way tapping the card does — on the day it is on **now**, which
     * is not necessarily the day it was on when the reminder was posted — and the ask is spent once shown.
     */
    @Test
    fun `a reminder's arrival opens its lesson's sheet on the day it is on`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", subj)
        val day = LocalDate.now(zone).plusDays(2)
        val id = env.fixtures.lesson(
            subjectId = subj, students = listOf(ann), start = day.atTime(10, 0).atZone(zone).toInstant(),
        ).single()
        val vm = vm()
        observing(vm.askedSheet)
        advanceUntilIdle()

        vm.arrive(Arrival(ScheduleTarget.Lesson(id)))
        advanceUntilIdle()

        assertEquals(day, vm.selectedDate.value)
        assertEquals(id, vm.askedSheet.value?.klass?.id)

        vm.sheetShown()
        advanceUntilIdle()
        assertNull("shown once, not again on the next page load", vm.askedSheet.value)
    }

    /** The sheet waits for a row, and a row the filter narrows away is never drawn — so the filter goes. */
    @Test
    fun `a reminder for somebody the schedule is not showing lifts the filter`() = runTest(mainRule.dispatcher) {
        val today = LocalDate.now(zone)
        val (ann, bob) = seedTwoStudentsOn(today)
        val bobs = env.classRepo.observeBetween(
            today.atStartOfDay(zone).toInstant(),
            today.plusDays(1).atStartOfDay(zone).toInstant(),
        ).first().single { it.studentId == bob }.id
        val vm = vm()
        observing(vm.studentFilter)
        observing(vm.askedSheet)
        vm.setStudentFilter(ann)
        advanceUntilIdle()

        vm.arrive(Arrival(ScheduleTarget.Lesson(bobs)))
        advanceUntilIdle()

        assertNull(vm.studentFilter.value)
        assertEquals(bobs, vm.askedSheet.value?.klass?.id)
    }

    /** The tray named a lesson; a schedule with no sign of it would read as the app having lost it. */
    @Test
    fun `a reminder for a lesson since deleted says so`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val seen = mutableListOf<ScheduleViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }

        vm.arrive(Arrival(ScheduleTarget.Lesson(404L)))
        advanceUntilIdle()

        assertEquals(listOf(ScheduleViewModel.Event.LESSON_GONE), seen)
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
                    durationMinutes = 60, status = LessonStatus.SCHEDULED,
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
                durationMinutes = 60, status = LessonStatus.SCHEDULED,
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

    /**
     * **Every view reads the one week that holds the selected day**, so switching view inside it reads
     * nothing at all.
     *
     * The views used to assemble their own — a day for «День», a week for «Неделя» — and every switch
     * re-keyed the query: «Неделя» → «День» stood empty for the frames the re-read took, although every
     * lesson on that day was already in hand.
     */
    @Test
    fun `switching view inside the week reads nothing new`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val today = LocalDate.now(zone)
        // One class today, one on another day of the same week. Picked by excluding today rather
        // than as startOfWeek + 6: on the last day of the week that *is* today, and both lessons
        // would land on the selected day — failing for the calendar, not for the behaviour.
        val otherDay = CalendarLayout.week(CalendarLayout.startOfWeek(today)).first { it != today }
        env.fixtures.storedClass(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)),
                durationMinutes = 60, status = LessonStatus.SCHEDULED,
            )
        env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = startMillisOn(otherDay, LocalTime.of(9, 0)),
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        assertEquals("the day view shows the selected day", 1, vm.dayContent.rows.size)
        val week = vm.content.weekContent
        assertEquals("out of the week that holds it", 2, week.sections.sumOf { it.rows.size })

        vm.setViewMode(ScheduleViewMode.WEEK)
        advanceUntilIdle()

        assertSame("the week view reads the same week, not a new query's", week, vm.content.weekContent)
    }

    @Test
    fun `the day view loads no markers at all`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        env.fixtures.storedClass(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)),
                durationMinutes = 60, status = LessonStatus.SCHEDULED,
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
                        durationMinutes = 60, status = LessonStatus.SCHEDULED,
                    )
            }
        env.fixtures.storedClass(
                studentId = sid, subjectId = subj, startMillis = startMillisOn(otherMonth, LocalTime.of(9, 0)),
                durationMinutes = 60, status = LessonStatus.CANCELLED,
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
                durationMinutes = 60, status = LessonStatus.COMPLETED,
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
        val id = env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 90, priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED)

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.COMPLETED)
        advanceUntilIdle()

        val saved = env.classDao.items.value.single { it.id == id }
        assertEquals(LessonStatus.COMPLETED, saved.status)
        assertEquals(Money.ofMajor(45).minor, saved.chargeMinor) // 30/h * 90 min
    }

    // --- Move: the destination hour has to be free ---

    @Test
    fun `a lesson cannot be moved onto an hour another lesson already holds`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        val moving = env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)), durationMinutes = 60, status = LessonStatus.SCHEDULED)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(15, 0)), durationMinutes = 60, status = LessonStatus.SCHEDULED)

        val vm = vm()
        vm.moveTo(env.classRepo.getSessionFor(moving)!!, day, LocalTime.of(15, 30))
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
        val cancelled = env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = LessonStatus.CANCELLED)
        // The freed hour, re-booked for somebody else.
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = LessonStatus.SCHEDULED)

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(cancelled)!!, LessonStatus.SCHEDULED)
        advanceUntilIdle()

        assertEquals("Борис", vm.timeConflict.value?.who)
        assertEquals(
            "the lesson stayed cancelled",
            LessonStatus.CANCELLED,
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
                durationMinutes = 60, status = LessonStatus.CANCELLED,
            )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(cancelled)!!, LessonStatus.SCHEDULED)
        advanceUntilIdle()

        assertNull("nothing holds the hour, so nothing to refuse", vm.timeConflict.value)
        assertEquals(LessonStatus.SCHEDULED, env.classDao.items.value.single().status)
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
                durationMinutes = 60, status = LessonStatus.SCHEDULED,
            )
        val vm = ScheduleViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.homeworkRepo, mainRule.appScope,
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
                durationMinutes = 60, status = LessonStatus.SCHEDULED,
            )
        val vm = ScheduleViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.homeworkRepo, mainRule.appScope,
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
                durationMinutes = 60, status = LessonStatus.SCHEDULED,
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
                durationMinutes = 60, status = LessonStatus.SCHEDULED, priceMinor = 3000,
            )

        val vm = vm()
        vm.setStatus(env.classRepo.getSessionFor(id)!!, LessonStatus.COMPLETED)
        advanceUntilIdle()

        assertNull(vm.timeConflict.value)
        assertEquals(LessonStatus.COMPLETED, env.classDao.items.value.single().status)
    }

    @Test
    fun `a lesson can be nudged within its own hour`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val day = LocalDate.of(2026, 7, 15)
        val id = env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = startMillisOn(day, LocalTime.of(9, 0)), durationMinutes = 60, status = LessonStatus.SCHEDULED)

        val vm = vm()
        // Ten minutes later overlaps where it currently is — it must not block itself.
        vm.moveTo(env.classRepo.getSessionFor(id)!!, day, LocalTime.of(9, 10))
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
        val first = env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = LessonStatus.SCHEDULED, groupId = groupId)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = LessonStatus.SCHEDULED, groupId = groupId)

        val vm = vm()
        vm.moveTo(env.classRepo.getSessionFor(first)!!, day, LocalTime.of(12, 30))
        advanceUntilIdle()

        assertNull(vm.timeConflict.value)
        val moved = startMillisOn(day, LocalTime.of(12, 30))
        assertEquals(
            "both members moved together",
            listOf(moved, moved),
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
        env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = LessonStatus.SCHEDULED, groupId = groupId)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = LessonStatus.SCHEDULED, groupId = groupId)

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
        env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, status = LessonStatus.SCHEDULED, groupId = groupId)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, status = LessonStatus.SCHEDULED, groupId = groupId)
        // Plus an unrelated individual class earlier the same day.
        env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = startMillisOn(today, LocalTime.of(9, 0)), durationMinutes = 60, status = LessonStatus.SCHEDULED)

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        val rows = vm.dayContent.rows
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
        env.fixtures.storedClass(studentId = ann, subjectId = subj, startMillis = at, durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED, groupId = groupId)
        env.fixtures.storedClass(studentId = bob, subjectId = subj, startMillis = at, durationMinutes = 60, priceMinor = Money.ofMajor(20).minor, status = LessonStatus.SCHEDULED, groupId = groupId)

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStatus(vm.dayContent.rows.single().session, LessonStatus.COMPLETED)
        advanceUntilIdle()

        val saved = env.classDao.items.value
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
        val id = env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = LessonStatus.CANCELLED)

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

    /**
     * "Billed" is the billing rule's, not "completed": a lesson that went ahead with every student marked
     * absent earned nothing, so deleting it takes no income — and warning that it does would be a false
     * alarm about money. One absentee in a group is not enough: the others still billed.
     */
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
        assertEquals(2, vm.dayContent.rows.size)
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
            assertEquals(1, vm.dayContent.rows.size)
            assertEquals("Аня", vm.dayContent.rows.single().title)
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
        assertEquals(2, vm.dayContent.rows.size)
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

        val row = vm.dayContent.rows.single()
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

        assertTrue(vm.dayContent.rows.isEmpty())
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
        assertEquals(2, vm.dayContent.rows.size)
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

    // --- Why an empty schedule is empty ---

    /**
     * The three cases, as a rule rather than as two copies in two composables.
     *
     * The day and month views weighed three of these and the week view two, so a tutor with nothing set
     * up met «Добавить ученика» on «День» and a dead end on «Неделя» — an empty list carrying no way out
     * of itself, which is the one thing `EmptyState` exists to prevent. Neither copy could be tested.
     */
    @Test
    fun `an empty schedule says which kind of empty it is`() {
        assertEquals(
            ScheduleEmpty.NEEDS_STUDENT,
            ScheduleEmpty.of(filtered = false, canAddClass = false),
        )
        assertEquals(ScheduleEmpty.QUIET, ScheduleEmpty.of(filtered = false, canAddClass = true))
        // A narrowed schedule is not a quiet one, and says so however much could be booked.
        assertEquals(ScheduleEmpty.FILTERED, ScheduleEmpty.of(filtered = true, canAddClass = true))
        assertEquals(
            "a filter outranks having nothing set up: the way out is the chip either way",
            ScheduleEmpty.FILTERED,
            ScheduleEmpty.of(filtered = true, canAddClass = false),
        )
    }

    @Test
    fun `an app with nothing set up offers the step that unblocks it`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.emptyReason)
        advanceUntilIdle()

        assertEquals(ScheduleEmpty.NEEDS_STUDENT, vm.emptyReason.value.valueOrNull())
    }

    @Test
    fun `a quiet day in a set-up app offers to book a lesson`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Математика")
        env.fixtures.pricedStudent("Аня", subject)

        val vm = vm()
        observing(vm.emptyReason)
        advanceUntilIdle()

        assertEquals(ScheduleEmpty.QUIET, vm.emptyReason.value.valueOrNull())
    }

    @Test
    fun `a narrowed schedule reports being narrowed, not quiet`() = runTest(mainRule.dispatcher) {
        val today = LocalDate.now(zone)
        val (ann, _) = seedTwoStudentsOn(today)

        val vm = vm()
        observing(vm.emptyReason)
        observing(vm.filterableStudents)
        advanceUntilIdle()
        assertEquals(ScheduleEmpty.QUIET, vm.emptyReason.value.valueOrNull())

        vm.setStudentFilter(ann)
        advanceUntilIdle()

        assertEquals(ScheduleEmpty.FILTERED, vm.emptyReason.value.valueOrNull())
    }

    // --- The homework attached to a lesson ---------------------------------------------------------

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
        env.fixtures.homework(Assignee.Student(boris), text = "Бориса", at = env.clock.instant().plusSeconds(60))
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
