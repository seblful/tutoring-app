package com.seblful.klas.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShortcutsTest {

    private val all = Shortcuts.launcher + Shortcuts.widgetHeader + Shortcuts.widget

    @Test
    fun `a shortcut's link goes where its action does`() {
        all.forEach { shortcut ->
            val byAction = TrayNavigation.destinationFor(action = shortcut.action, classId = 0L)
            assertNotNull(byAction, shortcut.action)
            assertEquals(byAction, TrayNavigation.destinationForLink(shortcut.link), shortcut.link)
        }
    }

    /** A launcher keeps pinned shortcuts by id: renaming one would leave a dead icon on the home screen. */
    @Test
    fun `the launcher's shortcuts keep their ids and order`() {
        assertEquals(listOf("book", "today", "homework", "debts"), Shortcuts.launcher.map { it.id })
    }

    @Test
    fun `a lesson's link opens its sheet and other actions have no link`() {
        assertEquals(
            TrayNavigation.Destination.LessonOnSchedule(42),
            TrayNavigation.destinationForLink(TrayNavigation.lessonLink(42)),
        )
        assertNull(TrayNavigation.linkFor(TrayNavigation.ACTION_SHOW_LESSON))
    }
}
