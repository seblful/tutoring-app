package com.seblful.klas.format

import com.seblful.klas.domain.time.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone

/** A host clock that emits its current zone immediately and whenever the device zone changes. */
interface ObservableWallClock : WallClock {
    val zoneChanges: Flow<TimeZone>
}

/** Fixed clocks need no event source; device adapters implement [ObservableWallClock]. */
fun WallClock.zones(): Flow<TimeZone> =
    (this as? ObservableWallClock)?.zoneChanges ?: flowOf(zone)
