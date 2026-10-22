package com.seblful.klas.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.seblful.klas.R
import com.seblful.klas.di.appGraph
import com.seblful.klas.format.inLanguage
import com.seblful.klas.notifications.TrayNavigation

/**
 * Shortcuts into the app. Booking is the prominent one; a payment is not here because it needs a student
 * first, so debts — whose rows open the student who owes — stand in for it.
 */
class QuickActionsWidget : GlanceAppWidget() {

    // Exact renders for the size the widget is, without holding a view per supported size in the host.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val displayLocale = context.appGraph.displayLocale
        provideContent {
            val words = context.inLanguage(displayLocale.current)
            fun button(iconRes: Int, labelRes: Int, action: String) =
                ToolBarButton(iconRes, words.getString(labelRes), openApp(context, action))

            GlanceTheme(colors = KlasWidgetColors) {
                QuickActionsLayout(
                    appName = words.getString(R.string.app_name),
                    headerButton = button(
                        R.drawable.widget_icon_add,
                        R.string.schedule_add_class,
                        TrayNavigation.ACTION_BOOK_LESSON,
                    ),
                    buttons = listOf(
                        button(R.drawable.widget_icon_today, R.string.schedule_today, TrayNavigation.ACTION_OPEN_TODAY),
                        button(
                            R.drawable.widget_icon_add_student,
                            R.string.schedule_add_student,
                            TrayNavigation.ACTION_ADD_STUDENT,
                        ),
                        button(
                            R.drawable.widget_icon_homework,
                            R.string.nav_homework,
                            TrayNavigation.ACTION_OPEN_HOMEWORK,
                        ),
                        button(R.drawable.widget_icon_debts, R.string.debts_title, TrayNavigation.ACTION_OPEN_DEBTS),
                    ),
                )
            }
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
