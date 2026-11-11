package com.seblful.klas.widget

import android.content.Context
import android.content.Intent
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
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Shortcuts into the app. Booking is the prominent one; a payment is not here because it needs a student
 * first, so debts — whose rows open the student who owes — stand in for it.
 */
class QuickActionsWidget : GlanceAppWidget() {

    // Exact renders for the size the widget is, without holding a view per supported size in the host.
    override val sizeMode: SizeMode = SizeMode.Exact

    // The smallest shortcuts, and the 4x1 they are dropped as.
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(256.dp, 48.dp), DpSize(296.dp, 72.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locales = context.appGraph.widgets.displayLocale.observe()
        val initial = locales.first()
        provideContent {
            val locale by produceState(initial) { locales.collect { value = it } }
            Content(context, locale)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val locale = context.appGraph.widgets.displayLocale.observe().first()
        provideContent { Content(context, locale) }
    }

    @Composable
    private fun Content(context: Context, locale: Locale) {
        val words = Texts.of(locale.toLanguageTag())
        fun button(iconRes: Int, labelRes: StringResource, action: String) =
            ToolBarButton(iconRes, words.string(labelRes), openApp(context, action))

        GlanceTheme(colors = KlasWidgetColors) {
            QuickActionsLayout(
                appName = words.string(Strings.app_name),
                openApp = openApp(context, Intent.ACTION_MAIN),
                headerButton = button(
                    R.drawable.widget_icon_add,
                    Strings.schedule_add_class,
                    TrayNavigation.ACTION_BOOK_LESSON,
                ),
                buttons = listOf(
                    button(R.drawable.widget_icon_today, Strings.schedule_today, TrayNavigation.ACTION_OPEN_TODAY),
                    button(R.drawable.widget_icon_people, Strings.nav_people, TrayNavigation.ACTION_OPEN_PEOPLE),
                    button(
                        R.drawable.widget_icon_homework,
                        Strings.nav_homework,
                        TrayNavigation.ACTION_OPEN_HOMEWORK,
                    ),
                    button(R.drawable.widget_icon_debts, Strings.debts_title, TrayNavigation.ACTION_OPEN_DEBTS),
                ),
            )
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
