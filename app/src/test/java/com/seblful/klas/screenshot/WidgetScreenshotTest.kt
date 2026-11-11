package com.seblful.klas.screenshot

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Color
import android.view.View
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
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.testutil.texts
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import com.seblful.klas.widget.KlasWidgetColors
import com.seblful.klas.widget.QuickActionsLayout
import com.seblful.klas.widget.TodayLayout
import com.seblful.klas.widget.TodayRow
import com.seblful.klas.widget.ToolBarButton
import com.seblful.klas.widget.sampleRows
import com.seblful.klas.widget.todayTexts
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The home-screen widgets as the launcher draws them: each layout composed to RemoteViews at a cell size the
 * launcher offers, applied to a view, over a mid-grey wallpaper so the widget's own rounded edge shows.
 * Recorded and compared by the same Roborazzi tasks as the previews (see `docs/testing.md`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetScreenshotTest {

    @Test
    fun todayTwoByTwo() = today("2x2", TwoByTwo, lessons)

    @Test
    fun todayFourByTwo() = today("4x2", FourByTwo, lessons)

    @Test
    fun todayAtItsShortest() = today("shortest", DpSize(303.dp, 115.dp), lessons)

    @Test
    fun todayFree() = today("free", TwoByTwo, emptyList())

    @Test
    fun todayDark() = today("2x2-dark", TwoByTwo, lessons, night = true)

    /** What the widget picker shows on Android 15+, at the narrow size from which the title bar shows. */
    @Test
    fun todayPreview() = capture("Today.preview", DpSize(160.dp, 180.dp), night = false) { words ->
        TodayLayout(
            texts = words.todayTexts(),
            rows = words.sampleRows(hour12 = false, "ru"),
            openToday = Tap,
            bookLesson = Tap,
            openLesson = { Tap },
            markTaught = { Tap },
        )
    }

    @Test
    fun shortcutsFourByOne() = shortcuts("4x1", FourByOne)

    @Test
    fun shortcutsTwoByTwo() = shortcuts("2x2", TwoByTwo)

    @Test
    fun shortcutsThreeByThree() = shortcuts("3x3", DpSize(240.dp, 228.dp))

    @Test
    fun shortcutsOneByOne() = shortcuts("1x1", DpSize(56.dp, 56.dp))

    @Test
    fun shortcutsOneByFour() = shortcuts("1x4", DpSize(72.dp, 228.dp))

    @Test
    fun shortcutsDark() = shortcuts("4x1-dark", FourByOne, night = true)

    private val lessons = listOf(
        TodayRow(1, "Аня Соколова", "09:00 · Алгебра", taught = true, canMarkTaught = false),
        TodayRow(2, "11 «А»", "14:30 · Английский", taught = false, canMarkTaught = true),
        TodayRow(3, "Иван Петров", "17:00 · Физика", taught = false, canMarkTaught = true),
        TodayRow(4, "Маша Ковалёва", "19:00", taught = false, canMarkTaught = true),
    )

    private fun today(name: String, size: DpSize, rows: List<TodayRow>, night: Boolean = false) =
        capture("Today.$name", size, night) { words ->
            TodayLayout(
                texts = words.todayTexts(),
                rows = rows,
                openToday = Tap,
                bookLesson = Tap,
                openLesson = { Tap },
                markTaught = { Tap },
            )
        }

    private fun shortcuts(name: String, size: DpSize, night: Boolean = false) =
        capture("Shortcuts.$name", size, night) { words ->
            fun button(icon: Int, label: StringResource) = ToolBarButton(icon, words.string(label), Tap)
            QuickActionsLayout(
                appName = words.string(Strings.app_name),
                openApp = Tap,
                headerButton = button(R.drawable.widget_icon_add, Strings.schedule_add_class),
                buttons = listOf(
                    button(R.drawable.widget_icon_today, Strings.schedule_today),
                    button(R.drawable.widget_icon_people, Strings.nav_people),
                    button(R.drawable.widget_icon_homework, Strings.nav_homework),
                    button(R.drawable.widget_icon_debts, Strings.debts_title),
                ),
            )
        }

    private fun capture(name: String, size: DpSize, night: Boolean, content: @Composable (words: Texts) -> Unit) {
        if (night) RuntimeEnvironment.setQualifiers("+night")
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val words = Texts.of("ru")
        val widget = object : GlanceAppWidget() {
            override val sizeMode = SizeMode.Exact

            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
                GlanceTheme(colors = KlasWidgetColors) { content(words) }
            }
        }
        val remoteViews = runBlocking { widget.compose(activity, size = size) }

        val density = activity.resources.displayMetrics.density
        val width = (size.width.value * density).roundToInt()
        val height = (size.height.value * density).roundToInt()
        // A collection fills only under a widget host, as on a launcher; the grey stands in for a wallpaper.
        val host = AppWidgetHostView(activity).apply {
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.rgb(0x80, 0x80, 0x80))
        }
        host.addView(remoteViews.apply(activity, host), FrameLayout.LayoutParams(width, height))
        activity.setContentView(host, FrameLayout.LayoutParams(width, height))
        host.measure(exactly(width), exactly(height))
        host.layout(0, 0, width, height)

        host.captureRoboImage(
            "src/test/screenshots/Widget.$name.png",
            RoborazziOptions(compareOptions = CompareOptions(resultValidator = ThresholdValidator(ChangeThreshold))),
        )
    }

    private fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    private companion object {
        /** Cell sizes from the Glance canonical layouts' previews: a phone's 2x2, 4x2 and 4x1. */
        val TwoByTwo = DpSize(172.dp, 234.dp)
        val FourByTwo = DpSize(303.dp, 200.dp)
        val FourByOne = DpSize(296.dp, 72.dp)

        val Tap = actionStartActivity<MainActivity>()
    }
}
