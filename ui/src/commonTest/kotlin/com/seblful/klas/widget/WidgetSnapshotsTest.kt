package com.seblful.klas.widget

import com.seblful.klas.data.settings.Privacy
import com.seblful.klas.notifications.ShortcutIcon
import com.seblful.klas.text.Texts
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class WidgetSnapshotsTest {

    private val zone = TimeZone.of("Europe/Minsk")
    private val monday = LocalDate(2026, 6, 1)
    private val tuesday = LocalDate(2026, 6, 2)
    private val nine = Instant.parse("2026-06-01T09:00:00+03:00")
    private val anna = TodayLesson(7, nine, nine + 1.hours, "Anna", "Maths", taught = false, canMarkTaught = true)

    private fun build(
        days: List<Pair<LocalDate, List<TodayLesson>>> = listOf(monday to listOf(anna), tuesday to emptyList()),
        privacy: Privacy = Privacy(),
    ) = WidgetSnapshots.build(days, zone, Texts.of("en"), hour12 = false, privacy, icon = { it.name.lowercase() })

    @Test
    fun `today turns a page at each midnight and at each lesson's start and end`() {
        val pages = build().today.pages

        assertEquals(
            listOf(
                monday.atStartOfDayIn(zone),
                nine,
                nine + 1.hours,
                tuesday.atStartOfDayIn(zone),
            ).map { it.epochSeconds },
            pages.map { it.from },
        )
        assertEquals(listOf(false, true, false), pages.take(3).map { it.rows.single().onNow })
        assertTrue(pages.last().rows.isEmpty(), "Tuesday has no lessons")
    }

    @Test
    fun `a taught lesson is never on now and its row opens the lesson's sheet`() {
        val pages = build(listOf(monday to listOf(anna.copy(taught = true)))).today.pages

        assertFalse(pages.any { page -> page.rows.any { it.onNow } })
        val row = pages.first().rows.single()
        assertTrue(row.taught)
        assertEquals("klas://lesson/7", row.link)
        assertEquals("Anna", row.title)
    }

    @Test
    fun `lessons are hidden on a locked phone unless the tutor chose to show them`() {
        assertTrue(build().today.privateWhenLocked)
        assertFalse(build(privacy = Privacy(lockScreenWidgets = true)).today.privateWhenLocked)
    }

    @Test
    fun `the quick actions are the shared shortcuts with the platform's glyphs`() {
        val actions = build().quickActions

        assertEquals("klas://book", actions.header.link)
        assertEquals(ShortcutIcon.BOOK.name.lowercase(), actions.header.icon)
        assertEquals(
            listOf("klas://today", "klas://people", "klas://homework", "klas://debts"),
            actions.buttons.map { it.link },
        )
        assertTrue(actions.buttons.all { it.label.isNotBlank() })
    }

    @Test
    fun `a snapshot reads back as itself`() {
        val snapshot = build()
        assertEquals(snapshot, WidgetSnapshot.decode(snapshot.encode()))
    }
}
