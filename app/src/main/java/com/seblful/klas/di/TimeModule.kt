package com.seblful.klas.di

import android.content.Context
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.DeviceClock
import com.seblful.klas.format.deviceFirstDayOfWeek
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.datetime.DayOfWeek

/*
 * Where the app reads the wall clock, the device's time zone, and which day its week starts on.
 *
 * Most of the code needs neither injected: the domain takes `now` as a parameter, on the rule that
 * fixtures pin the clock rather than calling `Instant.now()`. What that rule cannot cover is code whose
 * whole job is *watching* the clock — `ScheduleViewModel.today` sleeps until the next local midnight
 * and re-reads the date — because there is no `now` to hand it, only a source to read repeatedly.
 *
 * A [WallClock] is the smallest thing that makes such code testable. `advanceTimeBy` moves a test
 * scheduler's *virtual* time, which `LocalDate.now()` knows nothing about, so a rollover asserted
 * against the system clock can only ever be asserted as "still today" — the interesting half is
 * unreachable. With this, a test moves both together and the midnight boundary becomes an ordinary
 * assertion.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object TimeModule {

    @Provides
    fun provideClock(@ApplicationContext context: Context): WallClock = DeviceClock(context)

    @Provides
    @FirstDayOfWeek
    fun provideFirstDayOfWeek(): DayOfWeek = deviceFirstDayOfWeek()
}
