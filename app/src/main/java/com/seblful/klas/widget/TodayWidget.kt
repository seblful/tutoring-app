package com.seblful.klas.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import java.util.Locale
import kotlin.time.Clock

/** Today's lessons on the home screen; the check marks one taught, as the schedule's swipe does. */
class TodayWidget : GlanceAppWidget() {

    // Exact renders for the size the widget is, without holding a view per supported size in the host.
    override val sizeMode: SizeMode = SizeMode.Exact

    // The smallest Today, and the sizes from which the title bar shows, narrow and wide.
    override val previewSizeMode = SizeMode.Responsive(
        setOf(DpSize(120.dp, 115.dp), DpSize(160.dp, 180.dp), DpSize(260.dp, 180.dp)),
    )

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val locale = context.appGraph.widgets.displayLocale.observe().first()
        val words = context.inLanguage(locale)
        val rows = words.sampleRows(hour12 = !DateFormat.is24HourFormat(context), locale)
        provideContent {
            GlanceTheme(colors = KlasWidgetColors) {
                TodayLayout(
                    texts = words.todayTexts(),
                    rows = rows,
                    openToday = openApp(context, TrayNavigation.ACTION_OPEN_TODAY),
                    bookLesson = openApp(context, TrayNavigation.ACTION_BOOK_LESSON),
                    openLesson = { openApp(context, TrayNavigation.ACTION_OPEN_TODAY) },
                    markTaught = { openApp(context, TrayNavigation.ACTION_OPEN_TODAY) },
                )
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgets = context.appGraph.widgets
        val states = combine(widgets.lessons.observe(), widgets.displayLocale.observe(), ::Pair)
        val initial = states.first()
        // Re-armed on every render, so a widget that outlived its process still turns the page at midnight.
        widgets.armMidnight()

        provideContent {
            val state by produceState(initial) { states.collect { value = it } }
            val (today, locale) = state
            val hour12 = !DateFormat.is24HourFormat(context)
            GlanceTheme(colors = KlasWidgetColors) {
                TodayLayout(
                    texts = context.inLanguage(locale).todayTexts(),
                    rows = today.map { it.toRow(hour12, locale) },
                    openToday = openApp(context, TrayNavigation.ACTION_OPEN_TODAY),
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

/** [TodayTexts] from [this], a context in the app's language. */
internal fun Context.todayTexts() = TodayTexts(
    title = getString(R.string.schedule_today),
    empty = getString(R.string.widget_today_empty),
    bookLesson = getString(R.string.schedule_add_class),
    markTaught = getString(R.string.class_mark_completed),
    taught = getString(R.string.class_status_completed),
)

/** The picker's sample day, from [this], a context in the app's language: the first lesson taught. */
internal fun Context.sampleRows(hour12: Boolean, locale: Locale): List<TodayRow> {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return resources.getStringArray(R.array.widget_preview_lessons).toList().chunked(2)
        .zip(listOf("09:00", "14:30", "17:00").map(LocalTime::parse))
        .mapIndexed { index, (lesson, time) ->
            TodayLesson(
                classId = index.toLong(),
                start = today.atTime(time).toInstant(TimeZone.currentSystemDefault()),
                who = lesson[0],
                subject = lesson[1],
                taught = index == 0,
                canMarkTaught = index != 0,
            ).toRow(hour12, locale)
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
        if (intent.action != WidgetUpdates.ACTION_NEW_DAY) return super.onReceive(context, intent)
        val graph = context.appGraph
        launchAsync(graph.applicationScope) { graph.widgets.refresh() }
    }

    override fun onDisabled(context: Context) {
        context.appGraph.widgets.disarmMidnight()
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
