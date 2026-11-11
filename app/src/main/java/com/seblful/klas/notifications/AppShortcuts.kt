package com.seblful.klas.notifications

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts

/**
 * The launcher's long-press menu on the app icon: the four things a tutor opens the app *to do*, each the
 * tray's own entry ([TrayNavigation]), so it lands where the widget's button of the same name does.
 *
 * Dynamic rather than `res/xml/shortcuts.xml`: a static shortcut names its package, and the debug and
 * benchmark builds carry a suffix — and only a published one can follow the app's language, not the phone's.
 */
object AppShortcuts {

    private class Entry(
        val id: String,
        val label: StringResource,
        @param:DrawableRes val icon: Int,
        val action: String,
    )

    private val entries = listOf(
        Entry("book", Strings.schedule_add_class, R.drawable.widget_icon_add, TrayNavigation.ACTION_BOOK_LESSON),
        Entry("today", Strings.nav_today, R.drawable.widget_icon_today, TrayNavigation.ACTION_OPEN_TODAY),
        Entry("homework", Strings.nav_homework, R.drawable.widget_icon_homework, TrayNavigation.ACTION_OPEN_HOMEWORK),
        Entry("debts", Strings.reports_debts, R.drawable.widget_icon_debts, TrayNavigation.ACTION_OPEN_DEBTS),
    )

    /** Publishes the shortcuts, labelled in [texts]' language. A launcher that refuses them costs nothing. */
    fun publish(context: Context, texts: Texts) {
        val shortcuts = entries.mapIndexed { rank, entry ->
            val label = texts.string(entry.label)
            ShortcutInfoCompat.Builder(context, entry.id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconCompat.createWithResource(context, entry.icon))
                .setIntent(Intent(entry.action, null, context, MainActivity::class.java))
                // The launcher's order, which it is otherwise free to choose: booking first, as on the widget.
                .setRank(rank)
                .build()
        }
        runCatchingCancellable { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
