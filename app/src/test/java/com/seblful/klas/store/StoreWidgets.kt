package com.seblful.klas.store

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.format.inLanguage
import com.seblful.klas.widget.KlasWidgetColors
import com.seblful.klas.widget.QuickActionsLayout
import com.seblful.klas.widget.TodayLayout
import com.seblful.klas.widget.TodayRow
import com.seblful.klas.widget.ToolBarButton
import com.seblful.klas.widget.toRow
import com.seblful.klas.widget.todayTexts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The home-screen widgets for the listing and the video, drawn as a launcher draws them (as in
 * `WidgetScreenshotTest`) but at the store phone's density, over nothing, so the home screen's wallpaper
 * shows round their corners. Today lists the demo day as the app has it at the end of the tour; a second
 * Today has the lesson on now ticked, as the video's tap leaves it.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal class StoreWidgets(private val graph: StoreGraph, private val locale: Locale, private val out: File) {

    /** Each capture's name and its marks: boxes in its pixels, as `StoreScreenshots` records them. */
    fun capture(): Map<String, Map<String, List<Int>>> {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        // Closed after: left open, its window lies over the app's in every screen captured after it.
        try {
            return capture(host.get())
        } finally {
            host.pause().stop().destroy()
        }
    }

    private fun capture(activity: Activity): Map<String, Map<String, List<Int>>> {
        val words = activity.inLanguage(locale)
        val texts = words.todayTexts()
        val now = graph.widgets.now()
        val hour12 = !DateFormat.is24HourFormat(activity)
        val rows = runBlocking { graph.widgets.lessons.observe().first() }.map { it.toRow(hour12, locale, now, texts) }
        val onNow = rows.single { it.onNow }
        val ticked = rows.map {
            if (it == onNow) it.copy(taught = true, canMarkTaught = false, onNow = false) else it
        }

        val today = mapOf("tick" to tick(onNow))
        return mapOf(
            "widget-today" to render(activity, "widget-today", TODAY) { today(words, rows) }.marks(today),
            "widget-today-ticked" to render(activity, "widget-today-ticked", TODAY) { today(words, ticked) }.marks(),
            "widget-shortcuts" to render(activity, "widget-shortcuts", SHORTCUTS) { shortcuts(words) }.marks(),
        )
    }

    @Composable
    private fun today(words: Context, rows: List<TodayRow>) = TodayLayout(
        texts = words.todayTexts(),
        rows = rows,
        openToday = Tap,
        bookLesson = Tap,
        openLesson = { Tap },
        markTaught = { Tap },
    )

    @Composable
    private fun shortcuts(words: Context) {
        fun button(icon: Int, label: Int) = ToolBarButton(icon, words.getString(label), Tap)
        QuickActionsLayout(
            appName = words.getString(R.string.app_name),
            openApp = Tap,
            headerButton = button(R.drawable.widget_icon_add, R.string.schedule_add_class),
            buttons = listOf(
                button(R.drawable.widget_icon_today, R.string.schedule_today),
                button(R.drawable.widget_icon_people, R.string.nav_people),
                button(R.drawable.widget_icon_homework, R.string.nav_homework),
                button(R.drawable.widget_icon_debts, R.string.debts_title),
            ),
        )
    }

    /**
     * What finds a row's tick: its description, which names the lesson. Walked by hand: `findViewsWithText`
     * does not reach into the list's rows.
     */
    private fun tick(row: TodayRow): (View) -> View? = { host ->
        val all = generateSequence(listOf(host)) { level ->
            level.flatMap { v -> if (v is ViewGroup) (0 until v.childCount).map(v::getChildAt) else emptyList() }
                .ifEmpty { null }
        }.flatten().toList()
        all.singleOrNull { it.contentDescription?.toString() == row.tickLabel }
            ?: error("No tick «${row.tickLabel}» among ${all.mapNotNull { it.contentDescription }}")
    }

    private class Rendered(val host: View) {
        fun marks(finders: Map<String, (View) -> View?> = emptyMap()): Map<String, List<Int>> =
            finders.mapValues { (name, find) ->
                val view = find(host) ?: error("No «$name» on the widget")
                val box = Rect(0, 0, view.width, view.height)
                (host as FrameLayout).offsetDescendantRectToMyCoords(view, box)
                listOf(box.left, box.top, box.right, box.bottom, 0)
            }
    }

    private fun render(activity: Activity, name: String, size: DpSize, content: @Composable () -> Unit): Rendered {
        val widget = object : GlanceAppWidget() {
            override val sizeMode = SizeMode.Exact

            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
                GlanceTheme(colors = KlasWidgetColors) { content() }
            }
        }
        val remoteViews = runBlocking { widget.compose(activity, size = size) }
        val density = activity.resources.displayMetrics.density
        val width = (size.width.value * density).roundToInt()
        val height = (size.height.value * density).roundToInt()
        val host = AppWidgetHostView(activity).apply {
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.TRANSPARENT)
        }
        host.addView(remoteViews.apply(activity, host), FrameLayout.LayoutParams(width, height))
        activity.setContentView(host, FrameLayout.LayoutParams(width, height))
        // Clear, so the widget's rounded corners come out transparent rather than in the window's colour.
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        host.measure(exactly(width), exactly(height))
        host.layout(0, 0, width, height)
        host.captureRoboImage(File(out, "$name.png"), RoborazziOptions(taskType = RoborazziTaskType.Record))
        return Rendered(host)
    }

    private fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    companion object {
        /**
         * A 4 × 4 Today, tall enough for the whole demo day, and a 4 × 1 shortcuts bar on the store's 412 dp
         * phone, as a launcher's grid sizes them; store/lib/phone.mjs's `HOME` places them at these sizes.
         */
        val TODAY = DpSize(380.dp, 400.dp)
        val SHORTCUTS = DpSize(380.dp, 76.dp)

        private val Tap = actionStartActivity<MainActivity>()
    }
}
