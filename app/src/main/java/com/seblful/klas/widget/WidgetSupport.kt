package com.seblful.klas.widget

import android.content.Context
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import com.seblful.klas.MainActivity
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.ui.theme.ContrastLevel
import com.seblful.klas.ui.theme.klasColorScheme
import com.seblful.klas.ui.theme.md_success
import com.seblful.klas.ui.theme.md_success_d

/**
 * The app's own palette, light and dark, as widget colours. Not the wallpaper's: the app uses dynamic
 * colour only when the tutor opts in, and a widget cannot read that setting without a database read
 * on every render.
 */
internal val KlasWidgetColors = ColorProviders(
    light = klasColorScheme(darkTheme = false, contrast = ContrastLevel.Standard),
    dark = klasColorScheme(darkTheme = true, contrast = ContrastLevel.Standard),
)

/** The app's green for a taught lesson, as the schedule colours its status. */
internal val TaughtColor = ColorProvider(day = md_success, night = md_success_d)

/**
 * Opens the app at [action], one of [TrayNavigation]'s, so a widget tap and a notification tap land
 * the same way.
 */
internal fun openApp(context: Context, action: String, classId: Long? = null): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { classId?.let { putExtra(TrayNavigation.EXTRA_CLASS_ID, it) } },
    )
