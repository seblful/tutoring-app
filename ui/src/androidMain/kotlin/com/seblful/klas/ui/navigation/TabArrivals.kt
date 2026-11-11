package com.seblful.klas.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.seblful.klas.ui.schedule.ScheduleTarget

/** Opens a tab and associates its request with the entry that actually received it. */
class TabArrivals internal constructor(private val navigator: KlasNavigator) {
    private var schedule by mutableStateOf<Pair<Long, Arrival<ScheduleTarget>>?>(null)
    private var debts by mutableStateOf<Pair<Long, Arrival<Unit>>?>(null)

    fun openSchedule(target: ScheduleTarget) {
        navigator.openTabFromTray(TopDestination.SCHEDULE)
        schedule = navigator.top.id to Arrival(target)
    }

    fun openDebts() {
        navigator.openTabFromTray(TopDestination.REPORTS)
        debts = navigator.top.id to Arrival(Unit)
    }

    fun forSchedule(entryId: Long): Arrival<ScheduleTarget>? =
        schedule?.takeIf { it.first == entryId }?.second

    fun forDebts(entryId: Long): Arrival<Unit>? =
        debts?.takeIf { it.first == entryId }?.second
}
