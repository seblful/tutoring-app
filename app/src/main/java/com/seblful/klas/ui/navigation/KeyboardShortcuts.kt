package com.seblful.klas.ui.navigation

import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts

/**
 * Ctrl-chords for a hardware keyboard — a tablet with a keyboard case, a Chromebook. Each stands for one of
 * the tray's own entries ([TrayNavigation]), so a chord goes exactly where the same tap in the tray or on
 * the widget would, and the system's shortcut list (Meta + /) names them from [group].
 */
object KeyboardShortcuts {

    class Chord(val keyCode: Int, val label: StringResource, val action: String)

    val chords: List<Chord> = listOf(
        Chord(KeyEvent.KEYCODE_1, Strings.nav_today, TrayNavigation.ACTION_OPEN_TODAY),
        Chord(KeyEvent.KEYCODE_2, Strings.nav_schedule, TrayNavigation.ACTION_OPEN_SCHEDULE),
        Chord(KeyEvent.KEYCODE_3, Strings.nav_people, TrayNavigation.ACTION_OPEN_PEOPLE),
        Chord(KeyEvent.KEYCODE_4, Strings.nav_homework, TrayNavigation.ACTION_OPEN_HOMEWORK),
        Chord(KeyEvent.KEYCODE_5, Strings.nav_reports, TrayNavigation.ACTION_OPEN_REPORTS),
        Chord(KeyEvent.KEYCODE_N, Strings.schedule_add_class, TrayNavigation.ACTION_BOOK_LESSON),
        Chord(KeyEvent.KEYCODE_COMMA, Strings.settings_title, TrayNavigation.ACTION_OPEN_SETTINGS),
    )

    /**
     * Bare keys, for when no text field has the keyboard (a field takes its own): N a new lesson, T today.
     * `/` searches too, but opens the screen in front's own search rather than going anywhere.
     */
    val keys: List<Chord> = listOf(
        Chord(KeyEvent.KEYCODE_N, Strings.schedule_add_class, TrayNavigation.ACTION_BOOK_LESSON),
        Chord(KeyEvent.KEYCODE_T, Strings.nav_today, TrayNavigation.ACTION_OPEN_TODAY),
    )

    /** The tray action the bare [keyCode] stands for, or null for any other key. */
    fun keyActionFor(keyCode: Int): String? = keys.firstOrNull { it.keyCode == keyCode }?.action

    /** The tray action [keyCode] stands for with Ctrl held, or null for any other key. */
    fun actionFor(keyCode: Int, ctrl: Boolean): String? =
        chords.firstOrNull { ctrl && it.keyCode == keyCode }?.action

    /** The chords and keys as the system lists them, in [texts]' language. */
    fun group(texts: Texts): KeyboardShortcutGroup = KeyboardShortcutGroup(
        texts.string(Strings.app_name),
        chords.map { KeyboardShortcutInfo(texts.string(it.label), it.keyCode, KeyEvent.META_CTRL_ON) } +
            keys.map { KeyboardShortcutInfo(texts.string(it.label), it.keyCode, 0) } +
            KeyboardShortcutInfo(texts.string(Strings.action_search), KeyEvent.KEYCODE_SLASH, 0),
    )
}
