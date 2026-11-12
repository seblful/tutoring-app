package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.CalendarLayout
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleContentTest : ScheduleTestFixture() {

    @Test
    fun `the day is Loading until its lessons have been read`() = runTest(main.dispatcher) {
        val vm = vm()

        assertEquals(Loadable.Loading, vm.content.value)
        assertEquals(Loadable.Loading, vm.content.value.day(vm.selectedDate.value))

        observing(vm.content)
        advanceUntilIdle()

        assertEquals(
            Loadable.Ready(ScheduleContent.Day(vm.selectedDate.value, emptyList(), DaySummary.NONE)),
            vm.content.value.day(vm.selectedDate.value),
        )
    }

    @Test
    fun `a week answers for the days it holds and no others`() {
        val monday = LocalDate(2026, 7, 13)
        val tuesday = monday.plus(1, DateTimeUnit.DAY)
        val summary = DaySummary.of(emptyList())
        val week: Loadable<ScheduleContent.Week> = Loadable.Ready(
            ScheduleContent.Week(monday, listOf(WeekSection(tuesday, emptyList(), summary))),
        )

        assertEquals(
            Loadable.Ready(ScheduleContent.Day(tuesday, emptyList(), summary)),
            week.day(tuesday),
        )
        assertEquals(
            Loadable.Ready(ScheduleContent.Day(monday.plus(6, DateTimeUnit.DAY), emptyList(), DaySummary.NONE)),
            week.day(monday.plus(6, DateTimeUnit.DAY)),
            "a day in the week with no section is free",
        )
        assertEquals(
            Loadable.Loading,
            week.day(monday.plus(7, DateTimeUnit.DAY)),
            "the next week is not this week's to answer",
        )
        assertEquals(Loadable.Loading, week.day(monday.minus(1, DateTimeUnit.DAY)))
    }

    @Test
    fun `a week that has not arrived answers for no day`() {
        val nothing: Loadable<ScheduleContent.Week> = Loadable.Loading

        assertEquals(Loadable.Loading, nothing.day(LocalDate(2026, 7, 15)))
    }

    @Test
    fun `the selected day carries its own summary`() = runTest(main.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj, rate = Money.ofMajor(30))
        val today = env.clock.today()
        env.fixtures.lesson(
            subjectId = subj, students = listOf(sid),
            start = today.atTime(9, 0).toInstant(zone),
            minutes = 90, rate = Money.ofMajor(30),
        )
        env.fixtures.cancelledLesson(
            subjectId = subj, students = listOf(sid),
            start = today.atTime(15, 0).toInstant(zone),
            rate = Money.ofMajor(30),
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        val summary = vm.dayContent.summary
        assertEquals(1, summary.lessons, "the cancelled one is not a lesson the day holds")
        assertEquals(1, summary.cancelled)
        assertEquals(90, summary.minutes)
        assertEquals(Money.ZERO, summary.money, "a planned lesson bills nothing yet")
    }

    @Test
    fun `add is disabled until at least one student and one subject exist`() = runTest(main.dispatcher) {
        val vm = vm()
        assertEquals(Loadable.Loading, vm.canAddClass.value, "nothing is claimed before the setup has been read")
        observing(vm.canAddClass)
        advanceUntilIdle()
        assertEquals(false, vm.canAddClass.value.valueOrNull())

        env.fixtures.student(name = "Аня")
        advanceUntilIdle()
        assertEquals(false, vm.canAddClass.value.valueOrNull(), "a student without any subject still can't book")

        env.fixtures.subject(name = "Математика")
        advanceUntilIdle()
        assertEquals(true, vm.canAddClass.value.valueOrNull())
    }

    @Test
    fun `rows show only the selected day's classes - time-sorted - with student and subject names`() =
        runTest(main.dispatcher) {
            val sid = env.fixtures.student(name = "Аня")
            val subj = env.fixtures.subject(name = "Математика")
            val today = env.clock.today()
            // Two classes today (out of order) and one tomorrow.
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = startMillisOn(today, LocalTime(15, 0)),
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            )
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = startMillisOn(today, LocalTime(9, 0)),
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            )
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = startMillisOn(today.plus(1, DateTimeUnit.DAY), LocalTime(9, 0)),
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            )

            val vm = vm()
            observing(vm.content)
            advanceUntilIdle()

            val rows = vm.dayContent.rows
            assertEquals(2, rows.size)
            assertTrue(rows[0].klass.start < rows[1].klass.start, "rows are sorted by start time")
            assertEquals("Аня", rows[0].title)
            assertEquals("Математика", rows[0].subjectName)
        }

    @Test
    fun `week sections group the whole week by day and skip empty days`() = runTest(main.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val other = env.fixtures.student(name = "Пётр")
        val subj = env.fixtures.subject(name = "Математика")
        val monday = LocalDate(2026, 7, 13)
        val thursday = LocalDate(2026, 7, 16)
        val nextWeek = LocalDate(2026, 7, 20)
        listOf(
            Triple(monday, LocalTime(15, 0), sid),
            Triple(monday, LocalTime(9, 0), other),
            Triple(thursday, LocalTime(11, 0), sid),
            Triple(nextWeek, LocalTime(9, 0), sid),
        ).forEach { (day, time, student) ->
            env.fixtures.storedClass(
                studentId = student, subjectId = subj, startMillis = startMillisOn(day, time),
                durationMinutes = 60, status = LessonStatus.SCHEDULED,
            )
        }

        val vm = vm()
        observing(vm.content)
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(LocalDate(2026, 7, 15))
        advanceUntilIdle()

        val sections = vm.content.weekContent.sections
        assertEquals(2, sections.size, "only days with classes get a section")
        assertEquals(monday, sections[0].date)
        assertEquals(thursday, sections[1].date)
        assertEquals(2, sections[0].rows.size)
        assertTrue(
            sections[0].rows[0].klass.start < sections[0].rows[1].klass.start,
            "rows within a day are time-sorted",
        )
        assertEquals("Пётр", sections[0].rows[0].title)
        assertEquals("Математика", sections[0].rows[0].subjectName)
    }

    @Test
    fun `a strip day finds its section - and a day's header cancels only its planned lessons`() =
        runTest(main.dispatcher) {
            val sid = env.fixtures.student(name = "Аня")
            val subj = env.fixtures.subject(name = "Математика")
            val monday = LocalDate(2026, 7, 13)
            val wednesday = LocalDate(2026, 7, 15)
            listOf(
                Triple(monday, LocalTime(9, 0), LessonStatus.SCHEDULED),
                Triple(wednesday, LocalTime(9, 0), LessonStatus.SCHEDULED),
                Triple(wednesday, LocalTime(11, 0), LessonStatus.CANCELLED),
            ).forEach { (day, time, status) ->
                env.fixtures.storedClass(
                    studentId = sid, subjectId = subj, startMillis = startMillisOn(day, time),
                    durationMinutes = 60, status = status,
                )
            }

            val vm = vm()
            observing(vm.content)
            vm.setViewMode(ScheduleViewMode.WEEK)
            vm.selectDate(wednesday)
            advanceUntilIdle()

            val week = vm.content.weekContent
            assertEquals(1, week.indexOfDay(monday), "after the strip")
            assertEquals(3, week.indexOfDay(wednesday), "after Monday's header and its one row")
            assertEquals(null, week.indexOfDay(LocalDate(2026, 7, 14)), "a day without lessons has no section")
            val wednesdayRows = week.sections[1].rows
            assertEquals(
                setOf(wednesdayRows.first { it.session.status == LessonStatus.SCHEDULED }.session.first.id),
                week.sections[1].plannedIds(),
            )
        }

    @Test
    fun `week sections follow the visible week - not the selected day`() = runTest(main.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        val nextWeek = LocalDate(2026, 7, 20)
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = startMillisOn(nextWeek, LocalTime(9, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        observing(vm.content)
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(LocalDate(2026, 7, 15))
        advanceUntilIdle()
        assertTrue(vm.content.weekContent.sections.isEmpty())

        vm.goToNextWeek()
        advanceUntilIdle()

        assertEquals(listOf(nextWeek), vm.content.weekContent.sections.map { it.date })
    }

    @Test
    fun `switching view inside the week reads nothing new`() = runTest(main.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        val today = env.clock.today()
        val otherDay = CalendarLayout.week(CalendarLayout.startOfWeek(today)).first { it != today }
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = startMillisOn(today, LocalTime(9, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
        )
        env.fixtures.storedClass(
            studentId = sid,
            subjectId = subj,
            startMillis = startMillisOn(otherDay, LocalTime(9, 0)),
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        assertEquals(1, vm.dayContent.rows.size, "the day view shows the selected day")
        val week = vm.content.weekContent
        assertEquals(2, week.sections.sumOf { it.rows.size }, "out of the week that holds it")

        vm.setViewMode(ScheduleViewMode.WEEK)
        advanceUntilIdle()

        assertSame(week, vm.content.weekContent, "the week view reads the same week, not a new query's")
    }

    @Test
    fun `the week agenda collapses a group lesson exactly like the day list`() = runTest(main.dispatcher) {
        val ann = env.fixtures.student(name = "Аня")
        val bob = env.fixtures.student(name = "Борис")
        val subj = env.fixtures.subject(name = "Алгебра")
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
        val wednesday = LocalDate(2026, 7, 15)
        val at = startMillisOn(wednesday, LocalTime(12, 0))
        env.fixtures.storedClass(
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
        observing(vm.content)
        vm.setViewMode(ScheduleViewMode.WEEK)
        vm.selectDate(wednesday)
        advanceUntilIdle()

        val section = vm.content.weekContent.sections.single()
        assertEquals(wednesday, section.date)
        assertEquals(1, section.rows.size, "two member classes, one agenda row")
        assertTrue(section.rows[0].isGroup)
        assertEquals("11 «А»", section.rows[0].title)
        assertEquals(listOf("Аня", "Борис"), section.rows[0].members)
    }

    @Test
    fun `a group lesson is one row - with its members inside it`() = runTest(main.dispatcher) {
        val ann = env.fixtures.student(name = "Аня")
        val bob = env.fixtures.student(name = "Борис")
        val subj = env.fixtures.subject(name = "Алгебра")
        val groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
        val today = env.clock.today()
        val at = startMillisOn(today, LocalTime(12, 0))
        env.fixtures.storedClass(
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
        env.fixtures.storedClass(
            studentId = ann,
            subjectId = subj,
            startMillis = startMillisOn(today, LocalTime(9, 0)),
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        val rows = vm.dayContent.rows
        assertEquals(2, rows.size, "the group's two classes collapse into one row")
        val individual = rows.first()
        val group = rows.last()
        assertFalse(individual.isGroup)
        assertEquals("Аня", individual.title)
        assertTrue(group.isGroup)
        assertEquals("11 «А»", group.title)
        assertEquals("Алгебра", group.subjectName)
        assertEquals(listOf("Аня", "Борис"), group.members)
        assertEquals(2, group.classes.size, "both members' classes are the one lesson")
    }
}
