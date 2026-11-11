package com.seblful.klas.ui.navigation

import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import com.seblful.klas.notifications.TrayNavigation
import com.seblful.klas.notifications.TrayNavigation.Destination
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts

/**
 * Ctrl-chords for a hardware keyboard — a tablet with a keyboard case, a Chromebook. Each stands for one of
 * the tray's own entries ([TrayNavigation]), so a chord goes exactly where the same tap in the tray or on
 * the widget would, and the system's shortcut list (Meta + /) names them from [group].
 */
object KeyboardShortcuts {

    class Chord(val keyCode: Int, val label: StringResource, val destination: Destination)

    val chords: List<Chord> = listOf(
        Chord(KeyEvent.KEYCODE_1, Strings.nav_today, Destination.Today),
        Chord(KeyEvent.KEYCODE_2, Strings.nav_schedule, Destination.Schedule),
        Chord(KeyEvent.KEYCODE_3, Strings.nav_people, Destination.People),
        Chord(KeyEvent.KEYCODE_4, Strings.nav_homework, Destination.Homework),
        Chord(KeyEvent.KEYCODE_5, Strings.nav_reports, Destination.Reports),
        Chord(KeyEvent.KEYCODE_N, Strings.schedule_add_class, Destination.NewLesson),
        Chord(KeyEvent.KEYCODE_COMMA, Strings.settings_title, Destination.Settings),
    )

    /**
     * Bare keys, for when no text field has the keyboard (a field takes its own): N a new lesson, T today.
     * `/` searches too, but opens the screen in front's own search rather than going anywhere.
     */
    val keys: List<Chord> = listOf(
        Chord(KeyEvent.KEYCODE_N, Strings.schedule_add_class, Destination.NewLesson),
        Chord(KeyEvent.KEYCODE_T, Strings.nav_today, Destination.Today),
    )

    /** Where the bare [keyCode] goes, or null for any other key. */
    fun keyDestinationFor(keyCode: Int): Destination? = keys.firstOrNull { it.keyCode == keyCode }?.destination

    /** Where [keyCode] goes with Ctrl held, or null for any other key. */
    fun destinationFor(keyCode: Int, ctrl: Boolean): Destination? =
        chords.firstOrNull { ctrl && it.keyCode == keyCode }?.destination

    /** The chords and keys as the system lists them, in [texts]' language. */
    fun group(texts: Texts): KeyboardShortcutGroup = KeyboardShortcutGroup(
        texts.string(Strings.app_name),
        chords.map { KeyboardShortcutInfo(texts.string(it.label), it.keyCode, KeyEvent.META_CTRL_ON) } +
            keys.map { KeyboardShortcutInfo(texts.string(it.label), it.keyCode, 0) } +
            KeyboardShortcutInfo(texts.string(Strings.action_search), KeyEvent.KEYCODE_SLASH, 0),
    )
}
