package com.seblful.klas.notifications

import com.seblful.klas.notifications.TrayNavigation.Destination
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutsTest {

    private val all = Shortcuts.launcher + Shortcuts.widgetHeader + Shortcuts.widget

    @Test
    fun `a shortcut's link opens its place`() {
        all.forEach { shortcut ->
            assertEquals(shortcut.destination, TrayNavigation.destinationForLink(shortcut.link), shortcut.link)
        }
    }

    /** A launcher keeps pinned shortcuts by id: renaming one would leave a dead icon on the home screen. */
    @Test
    fun `the launcher's shortcuts keep their ids and order`() {
        assertEquals(listOf("book", "today", "homework", "debts"), Shortcuts.launcher.map { it.id })
    }

    @Test
    fun `every destination reads back from its link`() {
        val every = listOf(
            Destination.Lesson(42),
            Destination.LessonOnSchedule(42),
            Destination.Debts,
            Destination.Today,
            Destination.Day(LocalDate(2026, 11, 11)),
            Destination.NewLesson,
            Destination.Homework,
            Destination.People,
            Destination.Schedule,
            Destination.Reports,
            Destination.Settings,
            Destination.Student(7),
        )
        every.forEach { destination ->
            val link = TrayNavigation.linkFor(destination)
            assertEquals(destination, TrayNavigation.destinationForLink(link), link)
        }
    }

    @Test
    fun `a lesson's sheet and its form have links of their own`() {
        assertEquals("klas://lesson/42", TrayNavigation.linkFor(Destination.LessonOnSchedule(42)))
        assertEquals("klas://lesson/42/edit", TrayNavigation.linkFor(Destination.Lesson(42)))
    }
}
