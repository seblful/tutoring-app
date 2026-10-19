package com.seblful.klas.format

import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Reading the phone's first-day-of-week preference off a locale.
 *
 * The device half of [deviceFirstDayOfWeek] — *which* locales to ask — needs a device; this is the half
 * that does not, and it is the half with the mapping in it. `fw` carries CLDR's three-letter day
 * abbreviations, which are not what `DayOfWeek.valueOf` accepts, so the translation is a table and a
 * table is a thing to get wrong.
 */
class WeekStartTest {

    private fun locale(tag: String) = Locale.forLanguageTag(tag)

    @Test
    fun `a locale carrying the preference states the day`() {
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeekOrNull(locale("ru-BY-u-fw-sun")))
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeekOrNull(locale("ru-BY-u-fw-mon")))
        assertEquals(DayOfWeek.SATURDAY, firstDayOfWeekOrNull(locale("en-US-u-fw-sat")))
    }

    /** Every day, so no arm of the table is a copy of its neighbour. */
    @Test
    fun `all seven days are reachable and distinct`() {
        val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
            .map { firstDayOfWeekOrNull(locale("ru-u-fw-$it")) }

        assertEquals(DayOfWeek.entries.toSet(), days.toSet())
        assertEquals("the order the table maps them in", DayOfWeek.entries.toList(), days)
    }

    /**
     * A locale with no preference on it answers nothing — *not* Monday. The fallback is the caller's, so
     * [deviceFirstDayOfWeek] can keep asking the next locale rather than stopping at the first one that
     * happens to be silent.
     */
    @Test
    fun `a locale with no preference answers nothing`() {
        assertNull(firstDayOfWeekOrNull(locale("ru-BY")))
        assertNull(firstDayOfWeekOrNull(locale("en-US")))
        assertNull(firstDayOfWeekOrNull(Locale.ROOT))
    }

    /** Other regional preferences ride the same extension; only `fw` is about the week. */
    @Test
    fun `a locale carrying other regional preferences is not read as a week start`() {
        assertNull(firstDayOfWeekOrNull(locale("ru-BY-u-mu-celsius-nu-latn")))
        assertEquals(
            DayOfWeek.SUNDAY,
            firstDayOfWeekOrNull(locale("ru-BY-u-fw-sun-mu-celsius")),
        )
    }

    /** Nonsense is a missing answer, not a crash: this reads whatever a phone happens to be set to. */
    @Test
    fun `an unrecognised preference answers nothing`() {
        assertNull(firstDayOfWeekOrNull(locale("ru-u-fw-xyz")))
        assertNull(firstDayOfWeekOrNull(locale("ru-u-fw-monday")))
    }
}
