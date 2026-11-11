package com.seblful.klas.notifications

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.notifications.TrayNavigation.Destination
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
    fun `a Ctrl-chord stands for the place of the same name, and a bare key for nothing`() {
        assertEquals(Destination.Today, KeyboardShortcuts.destinationFor(KeyEvent.KEYCODE_1, ctrl = true))
        assertEquals(Destination.Reports, KeyboardShortcuts.destinationFor(KeyEvent.KEYCODE_5, ctrl = true))
        assertEquals(Destination.NewLesson, KeyboardShortcuts.destinationFor(KeyEvent.KEYCODE_N, ctrl = true))
        assertNull(KeyboardShortcuts.destinationFor(KeyEvent.KEYCODE_N, ctrl = false))
        assertNull(KeyboardShortcuts.destinationFor(KeyEvent.KEYCODE_Q, ctrl = true))
    }

    @Test
    fun `a bare N books and a bare T opens today, and other keys mean nothing`() {
        assertEquals(Destination.NewLesson, KeyboardShortcuts.keyDestinationFor(KeyEvent.KEYCODE_N))
        assertEquals(Destination.Today, KeyboardShortcuts.keyDestinationFor(KeyEvent.KEYCODE_T))
        assertNull(KeyboardShortcuts.keyDestinationFor(KeyEvent.KEYCODE_Q))
        assertNull(KeyboardShortcuts.keyDestinationFor(KeyEvent.KEYCODE_SLASH))
    }

    @Test
    fun `a chord's intent arrives where the chord stands for`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardShortcuts.chords.forEach { chord ->
            assertEquals(chord.destination, context.openAppIntent(chord.destination).arrival())
        }
    }

    @Test
    fun `the launcher offers booking, today, homework and debts, each opening its place`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        AppShortcuts.publish(context, context.texts())

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertEquals(listOf("book", "today", "homework", "debts"), shortcuts.map { it.id })
        assertEquals(listOf("Book a lesson", "Today", "Homework", "Debts"), shortcuts.map { it.shortLabel.toString() })
        assertEquals(Shortcuts.launcher.map { it.destination }, shortcuts.map { it.intent.arrival() })
    }

    @Test
    fun `an intent from an older build still arrives`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = Intent(TrayNavigation.ACTION_OPEN_DEBTS, null, context, com.seblful.klas.MainActivity::class.java)

        assertEquals(Destination.Debts, legacy.arrival())
    }

    /** What `TrayArrivals` reads from [this]. */
    private fun Intent.arrival() = TrayNavigation.destinationFor(action, classId = 0, link = dataString)
}
