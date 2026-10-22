package com.seblful.klas.widget

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.seblful.klas.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Locale

/**
 * The widget picker's previews on Android 15+: each widget's own layout, drawn by its `providePreview`, rather
 * than the app icon. The platform rate-limits publishing them (twice an hour), and drops them when the app is
 * installed over, so they are published once per install and language.
 */
@SingleIn(AppScope::class)
@Inject
class WidgetPreviews(@param:ApplicationContext private val context: Context) {

    suspend fun publish(locale: Locale) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val published = "${context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime}/" +
            locale.toLanguageTag()
        if (prefs().getString(KEY_PUBLISHED, null) == published) return
        val manager = GlanceAppWidgetManager(context)
        val results = listOf(TodayWidgetReceiver::class, QuickActionsWidgetReceiver::class)
            .map { manager.setWidgetPreviews(it) }
        // Rate-limited is retried by the next process.
        if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
            prefs().edit { putString(KEY_PUBLISHED, published) }
        }
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "widget-previews"
        const val KEY_PUBLISHED = "published-for"
    }
}
