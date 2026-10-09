package com.seblful.klas.format

import android.content.res.Resources
import com.seblful.klas.R
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.schedule.DaySummary

/**
 * What a day comes to, in words: «4 занятия · 5 ч · 240 BYN» — or **null** when there is nothing to say
 * about it.
 *
 * **This is a wording table and nothing else.** Which clauses a day's sentence has, in what order, and the
 * two cases where it is not the ordinary one are [DaySummary.parts] — pure, and tested on the JVM. What is
 * left here is one `when` from a [DaySummary.Part] to a string, plus the join.
 *
 * That split is the whole point of the file. There are **two** readers of this sentence — the schedule's
 * day heading and the morning digest in the notification tray — and a digest that added a day up
 * differently from the screen it summarises would be the worst kind of wrong: both numbers plausible, one
 * of them the tutor's actual morning. While the *rules* lived in here they were unreachable from a test,
 * because this function takes [Resources] and there is no `Resources` on the JVM. Now the agreement rests
 * on a value the two share and only the words are Android-bound.
 *
 * Takes [Resources] rather than being a `@Composable` because a notification is built from a `Context` and
 * has no composition to read `stringResource` from. Plurals still come from the resources' own
 * configuration rather than from the folder the strings were loaded from, so **which** `Resources` is
 * handed in decides the quantity rule: a screen's come from `AppRoot`, a notification's from
 * `Context.inAppLanguage()`, and both are the language the app is set to.
 *
 * Null rather than an empty string, so a caller has to decide what "nothing to say" looks like: the
 * schedule draws no line at all, because its empty state already says it at length.
 *
 * @param currency what money is printed in — the display setting, passed rather than read, since neither
 *   caller can reach `LocalCurrency` (one is not in composition, the other is below the UI).
 */
fun daySummaryText(res: Resources, summary: DaySummary, currency: Currency): String? {
    val parts = summary.parts
    if (parts.isEmpty()) return null
    return bulletJoin(parts.map { it.text(res, currency) })
}

private fun DaySummary.Part.text(res: Resources, currency: Currency): String = when (this) {
    is DaySummary.Part.Lessons -> res.getQuantityString(R.plurals.lessons_count, count, count)
    is DaySummary.Part.Hours -> res.getString(R.string.hours_value, formatHours(minutes))
    is DaySummary.Part.Earned -> money.money(currency)
    is DaySummary.Part.Cancelled -> res.getString(R.string.schedule_summary_cancelled, count)
}
