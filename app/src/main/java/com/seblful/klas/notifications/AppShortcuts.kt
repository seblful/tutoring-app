package com.seblful.klas.notifications

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.seblful.klas.MainActivity
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.text.Texts
import com.seblful.klas.widget.drawable

/**
 * The launcher's long-press menu on the app icon: [Shortcuts.launcher], each opening the app with its tray action.
 *
 * Dynamic rather than `res/xml/shortcuts.xml`: a static shortcut names its package, and the debug and
 * benchmark builds carry a suffix — and only a published one can follow the app's language, not the phone's.
 */
object AppShortcuts {

    /** Publishes the shortcuts, labelled in [texts]' language. A launcher that refuses them costs nothing. */
    fun publish(context: Context, texts: Texts) {
        val shortcuts = Shortcuts.launcher.mapIndexed { rank, entry ->
            val label = texts.string(entry.label)
            ShortcutInfoCompat.Builder(context, entry.id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconCompat.createWithResource(context, entry.icon.drawable))
                .setIntent(Intent(entry.action, null, context, MainActivity::class.java))
                // The launcher's order, which it is otherwise free to choose: booking first, as on the widget.
                .setRank(rank)
                .build()
        }
        runCatchingCancellable { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
