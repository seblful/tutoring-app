package com.seblful.klas.ui.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.ui.schedule.ScheduleTarget

@Composable
internal fun OpensWhatTheTrayNames(
    navigator: KlasNavigator,
    onSchedule: (ScheduleTarget) -> Unit,
    onDebts: () -> Unit,
) {
    val activity = LocalActivity.current as? ComponentActivity

    // The schedule tab, and then the day or the lesson the entry is about. Restoring the tab's saved state
    // alone would land the tutor on whatever day they were last browsing, which for a notification saying
    // «3 занятия не отмечены» would show a week with none of them in it.
    fun toSchedule(target: ScheduleTarget) {
        navigator.openTabFromTray(TopDestination.SCHEDULE)
        onSchedule(target)
    }

    val open: (Intent) -> Unit = { intent ->
        val destination = TrayNavigation.destinationFor(
            action = intent.action,
            classId = intent.getLongExtra(TrayNavigation.EXTRA_CLASS_ID, 0L),
            epochDay = intent.takeIf { it.hasExtra(TrayNavigation.EXTRA_DAY) }
                ?.getLongExtra(TrayNavigation.EXTRA_DAY, 0L),
            fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0,
            link = intent.dataString,
        )
        navigator.arrive(destination, ::toSchedule, onDebts)
    }

    var launchIntentHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activity) {
        if (launchIntentHandled) return@LaunchedEffect
        launchIntentHandled = true
        activity?.intent?.let(open)
    }

    DisposableEffect(activity) {
        val listener = Consumer<Intent>(open)
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
}

/** Puts the tutor where [destination] asks — nowhere for null, which is the launcher's own intent. */
private fun KlasNavigator.arrive(
    destination: TrayNavigation.Destination?,
    toSchedule: (ScheduleTarget) -> Unit,
    onDebts: () -> Unit,
) {
    val tab = destination?.let(::tabOf)
    if (tab != null) {
        // A tab is all it asks for. Today's day is Today's tab — what is on, what is left to mark — rather
        // than the schedule's list.
        openTabFromTray(tab)
        return
    }
    when (destination) {
        is TrayNavigation.Destination.Lesson -> openLessonForm(destination.classId)
        is TrayNavigation.Destination.LessonOnSchedule -> toSchedule(ScheduleTarget.Lesson(destination.classId))
        // A form interrupted by a notification stays underneath, just as with Reschedule.
        TrayNavigation.Destination.Debts -> {
            openTabFromTray(TopDestination.REPORTS)
            onDebts()
        }
        is TrayNavigation.Destination.Day -> toSchedule(ScheduleTarget.Day(destination.date))
        TrayNavigation.Destination.NewLesson -> openOnTop(Routes.classEdit(0))
        TrayNavigation.Destination.NewStudent -> openOnTop(Routes.StudentAdd)
        TrayNavigation.Destination.Settings -> openOnTop(Routes.Settings)
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
