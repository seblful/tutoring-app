package com.seblful.klas.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import com.seblful.klas.data.settings.Privacy
import com.seblful.klas.di.appGraph
import com.seblful.klas.format.formatTime
import com.seblful.klas.launchAsync
import com.seblful.klas.notifications.ReminderActions
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.text.StringArrays
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Today's lessons on the home screen; the check marks one taught, as the schedule's swipe does. */
class TodayWidget : GlanceAppWidget() {

    // Exact renders for the size the widget is, without holding a view per supported size in the host.
    override val sizeMode: SizeMode = SizeMode.Exact

    // The smallest Today, and the sizes from which the title bar shows, narrow and wide.
    override val previewSizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 115.dp),
            DpSize(160.dp, TodayTitleBarMinHeight),
            DpSize(TodaySmallWidth, TodayTitleBarMinHeight),
        ),
    )

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val tag = context.appGraph.widgets.displayLocale.observe().first()
        val words = Texts.of(tag)
        val rows = words.sampleRows(hour12 = !DateFormat.is24HourFormat(context), tag)
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
        val states = combine(widgets.lessons.observe(), widgets.displayLocale.observe(), widgets.privacy(), ::Triple)
        val initial = states.first()
        // Re-armed on every render, so a widget that outlived its process still moves «On now» and turns the
        // page at midnight.
        widgets.armNextChange(initial.first)

        provideContent {
            val state by produceState(initial) { states.collect { value = it } }
            val (today, tag, privacy) = state
            val hour12 = !DateFormat.is24HourFormat(context)
            val words = Texts.of(tag)
            val shown = listsLessons(LocalAppWidgetOptions.current.hostCategory(), privacy)
            GlanceTheme(colors = KlasWidgetColors) {
                TodayLayout(
                    texts = words.todayTexts().let {
                        if (shown) it else it.copy(empty = words.string(Strings.widget_today_locked))
                    },
                    rows = if (shown) {
                        val now = widgets.now()
                        val texts = words.todayTexts()
                        today.map { it.toRow(hour12, tag, now, texts) }
                    } else {
                        emptyList()
                    },
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

/**
 * Whether Today may list lessons where it is placed: always on a home screen, on the lock screen only when
 * the tutor chose so — whose lessons they are is not for whoever picks up the phone.
 */
internal fun listsLessons(hostCategory: Int, privacy: Privacy): Boolean =
    hostCategory != AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD || privacy.lockScreenWidgets

private fun Bundle.hostCategory(): Int =
    getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)

/** [TodayTexts] from [this], a context in the app's language. */
internal fun Texts.todayTexts() = TodayTexts(
    title = string(Strings.schedule_today),
    empty = string(Strings.widget_today_empty),
    bookLesson = string(Strings.schedule_add_class),
    markTaught = string(Strings.class_mark_completed),
    taught = string(Strings.class_status_completed),
    onNow = string(Strings.class_status_in_progress),
    markLesson = string(Strings.widget_mark_lesson),
    lessonTaught = string(Strings.widget_lesson_taught),
)

/** The picker's sample day, from [this], a context in the app's language: the first lesson taught. */
internal fun Texts.sampleRows(hour12: Boolean, tag: String): List<TodayRow> {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return array(StringArrays.widget_preview_lessons).chunked(2)
        .zip(listOf("09:00", "14:30", "17:00").map(LocalTime::parse))
        .mapIndexed { index, (lesson, time) ->
            val start = today.atTime(time).toInstant(TimeZone.currentSystemDefault())
            TodayLesson(
                classId = index.toLong(),
                start = start,
                end = start + 1.hours,
                who = lesson[0],
                subject = lesson[1],
                taught = index == 0,
                canMarkTaught = index != 0,
            ).toRow(hour12, tag, now = Instant.DISTANT_PAST, texts = todayTexts())
        }
}

/** The row for this lesson as it stands at [now]: «10:00–11:00 · Maths», «On now» while it is taught. */
internal fun TodayLesson.toRow(hour12: Boolean, tag: String, now: Instant, texts: TodayTexts): TodayRow {
    val starts = start.formatTime(hour12, tag)
    val range = "$starts–${end.formatTime(hour12, tag)}"
    return TodayRow(
        classId = classId,
        title = who,
        supportingText = listOf(range, subject).filter { it.isNotBlank() }.joinToString(" · "),
        taught = taught,
        canMarkTaught = canMarkTaught,
        onNow = isOnAt(now),
        tickLabel = String.format(
            Locale.forLanguageTag(tag),
            if (taught) texts.lessonTaught else texts.markLesson,
            who,
            starts,
        ),
    )
}

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
