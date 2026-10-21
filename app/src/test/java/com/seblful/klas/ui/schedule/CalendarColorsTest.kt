package com.seblful.klas.ui.schedule

import com.seblful.klas.ui.common.contrastRatio
import com.seblful.klas.ui.theme.ContrastLevel
import com.seblful.klas.ui.theme.klasColorScheme
import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarColorsTest {

    @Test
    fun `every day number and weekday reads as text on the page`() {
        val failures = listOf(false, true).flatMap { dark ->
            ContrastLevel.entries.flatMap { level ->
                val scheme = klasColorScheme(dark, level)
                val days = listOf(true, false).flatMap { today ->
                    listOf(true, false).map { inMonth ->
                        "today=$today inMonth=$inMonth" to
                            dayNumberColor(scheme, isSelected = false, isToday = today, inMonth = inMonth)
                    }
                }
                val selected = "selected" to dayNumberColor(scheme, isSelected = true, isToday = false, inMonth = true)
                val weekdays = DayOfWeek.entries.map { it.name to weekdayColor(scheme, it) }
                (days + weekdays).mapNotNull { (name, color) ->
                    val ratio = contrastRatio(color, scheme.surface)
                    "$dark $level $name: $ratio".takeIf { ratio < WcagAaText }
                } + listOfNotNull(
                    contrastRatio(selected.second, scheme.primary).let { r ->
                        "$dark $level selected: $r".takeIf { r < WcagAaText }
                    },
                )
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val WcagAaText = 4.5f
    }
}
