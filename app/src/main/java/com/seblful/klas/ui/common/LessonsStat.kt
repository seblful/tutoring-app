package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.format.formatHours

/** The «Занятий» tile, shared by the student and group cards because they answer it the same way. */
@Composable
fun lessonsStat(totals: Taught.Totals): Stat = Stat(
    label = stringResource(R.string.stat_lessons),
    value = totals.lessons.toString(),
    hint = when (val hint = totals.hint) {
        null -> null
        is Taught.Hint.Upcoming -> stringResource(R.string.stat_upcoming, hint.count)
        is Taught.Hint.Cancelled -> stringResource(R.string.stat_cancelled, hint.count)
    },
)

/**
 * The «Часов» tile — the report's and the group card's. Hours are whole and decimal, never money, so the
 * value carries no unit: the label is the unit.
 */
@Composable
fun hoursStat(minutes: Int): Stat =
    Stat(label = stringResource(R.string.stat_hours), value = formatHours(minutes))

/** The «Заработано» tile — what the student's or the group's taught lessons brought in. */
@Composable
fun earnedStat(earned: Money): Stat =
    Stat(label = stringResource(R.string.stat_earned), value = earned.text())
