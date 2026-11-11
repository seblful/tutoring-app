package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.format.formatHours
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource

/** The «Занятий» tile. */
@Composable
fun lessonsStat(totals: Taught.Totals): Stat = Stat(
    label = stringResource(Strings.stat_lessons),
    value = totals.lessons.toString(),
    hint = when (val hint = totals.hint) {
        null -> null
        is Taught.Hint.Upcoming -> stringResource(Strings.stat_upcoming, hint.count)
        is Taught.Hint.Cancelled -> stringResource(Strings.stat_cancelled, hint.count)
    },
)

/**
 * The «Часов» tile — the report's and the group card's. Hours are whole and decimal, never money, so the
 * value carries no unit: the label is the unit.
 */
@Composable
fun hoursStat(minutes: Int): Stat =
    Stat(label = stringResource(Strings.stat_hours), value = formatHours(minutes))
