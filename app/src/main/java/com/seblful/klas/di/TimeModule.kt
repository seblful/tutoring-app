package com.seblful.klas.di

import com.seblful.klas.deviceFirstDayOfWeek
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.DayOfWeek
import javax.inject.Qualifier

/**
 * Where the app reads the wall clock, the device's time zone, and which day its week starts on.
 *
 * Most of the code needs neither injected: the domain takes `now` as a parameter, on the rule that
 * fixtures pin the clock rather than calling `Instant.now()`. What that rule cannot cover is code whose
 * whole job is *watching* the clock — `ScheduleViewModel.today` sleeps until the next local midnight
 * and re-reads the date — because there is no `now` to hand it, only a source to read repeatedly.
 *
 * A [Clock] is the smallest thing that makes such code testable. `advanceTimeBy` moves a test
 * scheduler's *virtual* time, which `LocalDate.now()` knows nothing about, so a rollover asserted
 * against the system clock can only ever be asserted as "still today" — the interesting half is
 * unreachable. With this, a test moves both together and the midnight boundary becomes an ordinary
 * assertion.
 */
/**
 * Where a week starts, for the schedule's calendar — the phone's answer, read once (see
 * [com.seblful.klas.deviceFirstDayOfWeek]).
 *
 * Injected for the same reason the [Clock] is: the week strip and the month grid are anchored on it, so a
 * test has to be able to say "this tutor's week starts on Sunday" without a device set that way. Read once
 * per component rather than observed, because changing a regional preference restarts the activity anyway.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirstDayOfWeek

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @FirstDayOfWeek
    fun provideFirstDayOfWeek(): DayOfWeek = deviceFirstDayOfWeek()
}
