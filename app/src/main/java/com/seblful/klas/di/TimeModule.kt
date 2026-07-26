package com.seblful.klas.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Where the app reads the wall clock and the device's time zone.
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
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
