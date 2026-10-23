package com.seblful.klas.ui.navigation

import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.annotation.StringRes
import com.seblful.klas.R
import com.seblful.klas.notifications.TrayNavigation

/**
 * Ctrl-chords for a hardware keyboard — a tablet with a keyboard case, a Chromebook. Each stands for one of
 * the tray's own entries ([TrayNavigation]), so a chord goes exactly where the same tap in the tray or on
 * the widget would, and the system's shortcut list (Meta + /) names them from [group].
 */
object KeyboardShortcuts {

    class Chord(val keyCode: Int, @param:StringRes val label: Int, val action: String)

    val chords: List<Chord> = listOf(
        Chord(KeyEvent.KEYCODE_1, R.string.nav_today, TrayNavigation.ACTION_OPEN_TODAY),
        Chord(KeyEvent.KEYCODE_2, R.string.nav_schedule, TrayNavigation.ACTION_OPEN_SCHEDULE),
        Chord(KeyEvent.KEYCODE_3, R.string.nav_people, TrayNavigation.ACTION_OPEN_PEOPLE),
        Chord(KeyEvent.KEYCODE_4, R.string.nav_homework, TrayNavigation.ACTION_OPEN_HOMEWORK),
        Chord(KeyEvent.KEYCODE_5, R.string.nav_reports, TrayNavigation.ACTION_OPEN_REPORTS),
        Chord(KeyEvent.KEYCODE_N, R.string.schedule_add_class, TrayNavigation.ACTION_BOOK_LESSON),
        Chord(KeyEvent.KEYCODE_COMMA, R.string.settings_title, TrayNavigation.ACTION_OPEN_SETTINGS),
    )

    /** The tray action [keyCode] stands for with Ctrl held, or null for any other key. */
    fun actionFor(keyCode: Int, ctrl: Boolean): String? =
        chords.firstOrNull { ctrl && it.keyCode == keyCode }?.action

    /** The chords as the system lists them, in [context]'s language. */
    fun group(context: Context): KeyboardShortcutGroup = KeyboardShortcutGroup(
        context.getString(R.string.app_name),
        chords.map { KeyboardShortcutInfo(context.getString(it.label), it.keyCode, KeyEvent.META_CTRL_ON) },
    )
}
