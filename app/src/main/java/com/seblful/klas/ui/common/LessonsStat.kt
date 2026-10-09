package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.format.formatHours

/**
 * The «Занятий» tile, shared by the student and group cards because they answer it the same way.
 *
 * The headline number is lessons **taught** — the one that pairs with the hours and the money beside it.
 * What that leaves out goes in the hint, and which of the two candidates gets the one line that fits is
 * [Taught.Totals.hint]'s decision, not this composable's: a `@Composable` can only be exercised on a
 * device, and until that rule moved it was the one thing both cards agreed on that neither could test.
 *
 * What is left here is the wording.
 */
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
