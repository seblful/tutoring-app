package com.seblful.klas.notifications

import android.view.KeyEvent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.testutil.texts
import com.seblful.klas.ui.navigation.KeyboardShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The ways into the app besides its icon: a keyboard chord and the launcher's long-press menu. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class EntryPointsTest {

    @Test
    fun `a Ctrl-chord stands for the tray entry of the same place, and a bare key for nothing`() {
        assertEquals(TrayNavigation.ACTION_OPEN_TODAY, KeyboardShortcuts.actionFor(KeyEvent.KEYCODE_1, ctrl = true))
        assertEquals(TrayNavigation.ACTION_OPEN_REPORTS, KeyboardShortcuts.actionFor(KeyEvent.KEYCODE_5, ctrl = true))
        assertEquals(TrayNavigation.ACTION_BOOK_LESSON, KeyboardShortcuts.actionFor(KeyEvent.KEYCODE_N, ctrl = true))
        assertNull(KeyboardShortcuts.actionFor(KeyEvent.KEYCODE_N, ctrl = false))
        assertNull(KeyboardShortcuts.actionFor(KeyEvent.KEYCODE_Q, ctrl = true))
    }

    @Test
    fun `a bare N books and a bare T opens today, and other keys mean nothing`() {
        assertEquals(TrayNavigation.ACTION_BOOK_LESSON, KeyboardShortcuts.keyActionFor(KeyEvent.KEYCODE_N))
        assertEquals(TrayNavigation.ACTION_OPEN_TODAY, KeyboardShortcuts.keyActionFor(KeyEvent.KEYCODE_T))
        assertNull(KeyboardShortcuts.keyActionFor(KeyEvent.KEYCODE_Q))
        assertNull(KeyboardShortcuts.keyActionFor(KeyEvent.KEYCODE_SLASH))
    }

    @Test
    fun `every chord goes somewhere the tray can send the tutor`() {
        KeyboardShortcuts.chords.forEach { chord ->
            val destination = TrayNavigation.destinationFor(chord.action, classId = 0)
            assertEquals("${chord.action} has a destination", true, destination != null)
        }
    }

    @Test
    fun `the launcher offers booking, today, homework and debts, each a tray entry`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        AppShortcuts.publish(context, context.texts())

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertEquals(listOf("book", "today", "homework", "debts"), shortcuts.map { it.id })
        assertEquals(listOf("Book a lesson", "Today", "Homework", "Debts"), shortcuts.map { it.shortLabel.toString() })
        shortcuts.forEach { shortcut ->
            assertEquals(true, TrayNavigation.destinationFor(shortcut.intent.action, classId = 0) != null)
        }
    }
}
