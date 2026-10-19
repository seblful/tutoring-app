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
import androidx.navigation.NavHostController
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.ui.schedule.ScheduleTarget

/**
 * Sends the tutor where a notification they tapped asks them to go — a lesson's form for a reminder's
 * «Перенести», the lesson's sheet for the reminder itself, the schedule on its day for the agenda, the
 * evening digest and the auto-completion report, and the reports' debts for the weekly digest.
 *
 * A notification cannot show a date picker or list who owes what, so each carries an action and this is
 * where it becomes navigation. **Which** destination an intent asks for is [TrayNavigation.destinationFor]'s
 * and not this composable's: the app is opened by the launcher far more often than by a notification, and
 * getting that wrong would yank the tutor onto a lesson every time they open the app.
 *
 * **Both arrivals, because they are different arrivals.** A cold start has the intent waiting on the
 * activity; an app already running gets it through `onNewIntent`, which is the common case — the tutor
 * pulls the shade down over the app they were just in. Handling only the first would make the button work
 * exactly once per process.
 *
 * The `rememberSaveable` guard is about rotation: the activity is rebuilt and hands back the *same*
 * launch intent, which without it would re-open the lesson the tutor had already navigated away from.
 */
@Composable
internal fun OpensWhatTheTrayNames(
    navController: NavHostController,
    onSchedule: (ScheduleTarget) -> Unit,
    onDebts: () -> Unit,
) {
    val activity = LocalActivity.current as? ComponentActivity

    // The schedule tab, and then the day or the lesson the entry is about. Restoring the tab's saved state
    // alone would land the tutor on whatever day they were last browsing, which for a notification saying
    // «3 занятия не отмечены» would show a week with none of them in it.
    fun toSchedule(target: ScheduleTarget) {
        navController.navigateToTab(TopDestination.SCHEDULE)
        onSchedule(target)
    }

    val open: (Intent) -> Unit = { intent ->
        val destination = TrayNavigation.destinationFor(
            action = intent.action,
            classId = intent.getLongExtra(TrayNavigation.EXTRA_CLASS_ID, 0L),
            epochDay = intent.takeIf { it.hasExtra(TrayNavigation.EXTRA_DAY) }
                ?.getLongExtra(TrayNavigation.EXTRA_DAY, 0L),
        )
        when (destination) {
            is TrayNavigation.Destination.Lesson -> navController.openLessonForm(destination.classId)
            is TrayNavigation.Destination.LessonOnSchedule ->
                toSchedule(ScheduleTarget.Lesson(destination.classId))
            // A **tab**, so it is switched to the way the bottom bar switches to it. Pushing «Отчёты» on
            // top of wherever the tutor was would leave the reports sitting above the schedule with a
            // back arrow and no tab selected.
            TrayNavigation.Destination.Debts -> {
                navController.navigateToTab(TopDestination.REPORTS)
                onDebts()
            }
            TrayNavigation.Destination.Today -> toSchedule(ScheduleTarget.Today)
            is TrayNavigation.Destination.Day -> toSchedule(ScheduleTarget.Day(destination.date))
            null -> Unit
        }
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
