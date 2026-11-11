package com.seblful.klas.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.ui.schedule.ScheduleTarget

@Composable
internal fun OpensWhatTheTrayNames(navigator: KlasNavigator) {
    PlatformArrivals { destination -> navigator.arrive(destination) }
}

/**
 * Hands [arrive] where the platform asks the app to open: Android's launch and new intents (a notification,
 * a shortcut, a `klas://` link), iOS's notification taps and links.
 */
@Composable
internal expect fun PlatformArrivals(arrive: (TrayNavigation.Destination?) -> Unit)

/** Puts the tutor where [destination] asks — nowhere for null, which is the launcher's own intent. */
private fun KlasNavigator.arrive(destination: TrayNavigation.Destination?) {
    val tab = destination?.let(::tabOf)
    if (tab != null) {
        // A tab is all it asks for. Today's day is Today's tab — what is on, what is left to mark — rather
        // than the schedule's list.
        openTabFromTray(tab)
        return
    }
    when (destination) {
        is TrayNavigation.Destination.Lesson -> openLessonForm(destination.classId)
        is TrayNavigation.Destination.LessonOnSchedule ->
            arrivals.openSchedule(ScheduleTarget.Lesson(destination.classId))
        // A form interrupted by a notification stays underneath, just as with Reschedule.
        TrayNavigation.Destination.Debts -> arrivals.openDebts()
        is TrayNavigation.Destination.Day -> arrivals.openSchedule(ScheduleTarget.Day(destination.date))
        TrayNavigation.Destination.NewLesson -> openOnTop(Routes.classEdit(0))
        TrayNavigation.Destination.Settings -> openOnTop(Routes.Settings)
        // Beside People's list on a wide window, as a tap on the student's row would put it.
        is TrayNavigation.Destination.Student -> {
            openTabFromTray(TopDestination.PEOPLE)
            replaceAbove(Routes.People, Routes.StudentDetail(destination.studentId))
        }
        else -> Unit
    }
}

/** The tab a destination is, when a tab is all it asks for. */
private fun tabOf(destination: TrayNavigation.Destination): TopDestination? = when (destination) {
    TrayNavigation.Destination.Today -> TopDestination.TODAY
    TrayNavigation.Destination.Schedule -> TopDestination.SCHEDULE
    TrayNavigation.Destination.People -> TopDestination.PEOPLE
    TrayNavigation.Destination.Homework -> TopDestination.HOMEWORK
    TrayNavigation.Destination.Reports -> TopDestination.REPORTS
    else -> null
}
