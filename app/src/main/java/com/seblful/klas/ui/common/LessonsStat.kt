package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.Taught

/**
 * The «Занятий» tile, shared by the student and group cards because they answer it the same way.
 *
 * The headline number is lessons **taught** — the one that pairs with the hours and the money beside
 * it. What that leaves out goes in the hint, and only one line fits, so: lessons still to come beat
 * lessons that fell through («впереди 3» is something to act on, «отменено 3» is not), and a card with
 * neither says nothing rather than printing a zero.
 */
@Composable
fun lessonsStat(totals: Taught.Totals): Stat = Stat(
    label = stringResource(R.string.stat_lessons),
    value = totals.lessons.toString(),
    hint = when {
        totals.upcoming > 0 -> stringResource(R.string.stat_upcoming, totals.upcoming)
        totals.cancelled > 0 -> stringResource(R.string.stat_cancelled, totals.cancelled)
        else -> null
    },
)
