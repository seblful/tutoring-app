package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Rule
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ScheduleTestFixture {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()
    protected val zone: TimeZone = env.clock.zone

    protected fun vm(
        clock: WallClock = env.clock,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ) =
        mainRule.own(
            ScheduleViewModel(
                env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.homeworkRepo, env.homeworkQuery,
                env.lessonChanges, env.lessonCompletions, mainRule.appScope,
                mainRule.dispatcher, clock, env.settingsRepo, weekStart,
            ),
        )

    protected fun startMillisOn(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).toInstant(zone).toEpochMilliseconds()

    protected val ScheduleViewModel.dayContent: ScheduleContent.Day
        get() = content.value.day(selectedDate.value).valueOrNull() ?: error(
            "The selected day ${selectedDate.value} has not been read: the content is ${content.value}. " +
                "Is `observing(vm.content)` missing, or does the scheduler need advancing?",
        )

    protected val StateFlow<Loadable<ScheduleContent.Week>>.weekContent: ScheduleContent.Week
        get() = value.valueOrNull() ?: error(
            "The week has not been read: its content is $value. Is `observing(…)` missing?",
        )

    protected fun at(hour: Int): Instant =
        LocalDate(2026, 5, 18).atTime(hour, 0).toInstant(zone)

    protected suspend fun seedTwoStudentsOn(day: LocalDate): Pair<Long, Long> {
        val subj = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", subj)
        val bob = env.fixtures.pricedStudent("Борис", subj)
        env.fixtures.lesson(
            subjectId = subj, students = listOf(ann),
            start = day.atTime(9, 0).toInstant(zone),
        )
        env.fixtures.lesson(
            subjectId = subj, students = listOf(bob),
            start = day.atTime(11, 0).toInstant(zone),
        )
        return ann to bob
    }
}
