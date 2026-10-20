package com.seblful.klas.format

import com.seblful.klas.domain.time.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClockChangesTest {
    @Test
    fun anyHostAdapterCanEmitZoneChanges() = runTest {
        val zones = MutableStateFlow<TimeZone>(TimeZone.UTC)
        val clock = object : ObservableWallClock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(0)
            override val zone: TimeZone get() = zones.value
            override val zoneChanges = zones
        }
        val seen = mutableListOf<TimeZone>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { clock.zones().take(2).toList(seen) }
        zones.value = TimeZone.of("Europe/Minsk")
        collector.join()
        assertEquals(listOf(TimeZone.UTC, zones.value), seen)
    }

    @Test
    fun fixedClocksEmitTheirChosenZone() = runTest {
        val clock = WallClock.fixed(Instant.fromEpochMilliseconds(0), TimeZone.of("America/New_York"))
        assertEquals(listOf(clock.zone), clock.zones().toList())
    }
}
