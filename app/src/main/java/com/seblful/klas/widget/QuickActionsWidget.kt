package com.seblful.klas.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.seblful.klas.R
import com.seblful.klas.di.appGraph
import com.seblful.klas.notifications.Shortcut
import com.seblful.klas.notifications.ShortcutIcon
import com.seblful.klas.notifications.Shortcuts
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import kotlinx.coroutines.flow.first

/** Shortcuts into the app: [Shortcuts.widgetHeader] prominent, then [Shortcuts.widget]. */
class QuickActionsWidget : GlanceAppWidget() {

    // Exact renders for the size the widget is, without holding a view per supported size in the host.
    override val sizeMode: SizeMode = SizeMode.Exact

    // The smallest shortcuts, and the 4x1 they are dropped as.
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(256.dp, 48.dp), DpSize(296.dp, 72.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val languages = context.appGraph.widgets.displayLocale.observe()
        val initial = languages.first()
        provideContent {
            val tag by produceState(initial) { languages.collect { value = it } }
            Content(context, tag)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val tag = context.appGraph.widgets.displayLocale.observe().first()
        provideContent { Content(context, tag) }
    }

    @Composable
    private fun Content(context: Context, tag: String) {
        val words = Texts.of(tag)
        fun button(shortcut: Shortcut) =
            ToolBarButton(shortcut.icon.drawable, words.string(shortcut.label), openApp(context, shortcut.destination))

        GlanceTheme(colors = KlasWidgetColors) {
            QuickActionsLayout(
                appName = words.string(Strings.app_name),
                openApp = openApp(context, destination = null),
                headerButton = button(Shortcuts.widgetHeader),
                buttons = Shortcuts.widget.map(::button),
            )
        }
    }
}

/** The widget's and the launcher's drawable for [this] glyph. */
@get:DrawableRes
internal val ShortcutIcon.drawable: Int
    get() = when (this) {
        ShortcutIcon.BOOK -> R.drawable.widget_icon_add
        ShortcutIcon.TODAY -> R.drawable.widget_icon_today
        ShortcutIcon.PEOPLE -> R.drawable.widget_icon_people
        ShortcutIcon.HOMEWORK -> R.drawable.widget_icon_homework
        ShortcutIcon.DEBTS -> R.drawable.widget_icon_debts
    }

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
