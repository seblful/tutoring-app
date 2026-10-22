package com.seblful.klas.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import com.seblful.klas.R
import com.seblful.klas.di.appGraph
import com.seblful.klas.format.formatTime
import com.seblful.klas.format.inLanguage
import com.seblful.klas.launchAsync
import com.seblful.klas.notifications.ReminderActions
import com.seblful.klas.notifications.TrayNavigation
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Today's lessons on the home screen; the check marks one taught, as the schedule's swipe does. */
class TodayWidget : GlanceAppWidget() {

    // Exact renders for the size the widget is, without holding a view per supported size in the host.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = context.appGraph
        val lessons = graph.todayWidget.lessons.observe()
        val initial = lessons.first()
        // Re-armed on every render, so a widget that outlived its process still turns the page at midnight.
        graph.todayWidget.armMidnight()

        provideContent {
            val today by produceState(initial) { lessons.collect { value = it } }
            val locale = graph.displayLocale.current
            val words = context.inLanguage(locale)
            val hour12 = !DateFormat.is24HourFormat(context)
            GlanceTheme(colors = KlasWidgetColors) {
                TodayLayout(
                    texts = TodayTexts(
                        title = words.getString(R.string.schedule_today),
                        empty = words.getString(R.string.widget_today_empty),
                        bookLesson = words.getString(R.string.schedule_add_class),
                        markTaught = words.getString(R.string.class_mark_completed),
                        taught = words.getString(R.string.class_status_completed),
                    ),
                    rows = today.map { it.toRow(hour12, locale) },
                    bookLesson = openApp(context, TrayNavigation.ACTION_BOOK_LESSON),
                    openLesson = { classId -> openApp(context, TrayNavigation.ACTION_SHOW_LESSON, classId) },
                    markTaught = { classId ->
                        actionRunCallback<MarkTaughtAction>(actionParametersOf(MarkTaughtAction.ClassId to classId))
                    },
                )
            }
        }
    }
}

internal fun TodayLesson.toRow(hour12: Boolean, locale: Locale): TodayRow = TodayRow(
    classId = classId,
    title = who,
    supportingText = listOf(start.formatTime(hour12, locale), subject).filter { it.isNotBlank() }.joinToString(" · "),
    taught = taught,
    canMarkTaught = canMarkTaught,
)

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TodayWidgetUpdates.ACTION_NEW_DAY) return super.onReceive(context, intent)
        val graph = context.appGraph
        launchAsync(graph.applicationScope) { graph.todayWidget.refresh() }
    }

    override fun onDisabled(context: Context) {
        context.appGraph.todayWidget.disarmMidnight()
        super.onDisabled(context)
    }
}

/**
 * The widget's check. [ReminderActions.complete] is the reminder's «Проведено»: the same legality check
 * as the schedule's menu, so a stale widget cannot mark a cancelled lesson taught, and the lesson's
 * reminder leaves the tray with it.
 */
class MarkTaughtAction : ActionCallback {

    @Inject lateinit var actions: ReminderActions

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appGraph.inject(this)
        val classId = parameters[ClassId] ?: return
        actions.complete(classId)
        TodayWidget().update(context, glanceId)
    }

    companion object {
        val ClassId = ActionParameters.Key<Long>("classId")
    }
}
